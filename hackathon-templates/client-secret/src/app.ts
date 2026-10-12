import { randomBytes } from "node:crypto";
import Fastify, { LogController } from "fastify";
import cookie from "@fastify/cookie";
import { AppError, type Config } from "./config.ts";
import { escapeHtml, page } from "./http.ts";
import * as reference from "./reference.ts";
import { steps, type Steps } from "./steps.ts";
import { configureServer } from "./server.ts";

export async function buildApp(config: Config, referenceMode = false, implementation?: Steps) {
  const app = Fastify({ logger: true, logController: new LogController({ disableRequestLogging: true }) });
  configureServer(app);
  await app.register(cookie);
  const api = implementation ?? (referenceMode ? reference : steps);
  const transactions = new Map<string, reference.Transaction>();
  const sessions = new Map<string, {
    tokens: reference.Tokens; context: reference.Context; expiresAt: number;
  }>();
  const random = () => randomBytes(32).toString("base64url");
  const cookieOptions = {
    path: "/", httpOnly: true, sameSite: "lax" as const,
    secure: new URL(config.redirectUri).protocol === "https:",
  };
  app.addHook("onRequest", async () => {
    const now = Date.now();
    for (const [key, value] of transactions) if (now - value.createdAt > 300_000) transactions.delete(key);
    for (const [key, value] of sessions) if (value.expiresAt <= now) sessions.delete(key);
  });

  app.get("/", async (_request, reply) => reply.type("text/html").send(page(
    `SMART ${config.variant} ${referenceMode ? "reference" : "starter"}`,
    "<p>Launch this app from the EPJ app picker. Opening this page does not create patient context.</p>",
  )));
  app.get("/launch", async (request, reply) => {
    if (transactions.size >= 1000) throw new AppError(503, "Too many pending launches; try again later.");
    const launch = api.readLaunch(request.query, config);
    const discovery = await api.discover(config);
    const transaction = api.newTransaction(launch, discovery, api.pkce());
    const transactionId = random();
    const previous = request.cookies.smart_launch;
    if (previous) transactions.delete(previous);
    transactions.set(transactionId, transaction);
    reply.setCookie("smart_launch", transactionId, { ...cookieOptions, maxAge: 300 });
    return reply.redirect(api.authorizationUrl(config, transaction));
  });
  app.get("/callback", async (request, reply) => {
    const transactionId = request.cookies.smart_launch;
    const transaction = transactionId ? transactions.get(transactionId) : undefined;
    if (!transactionId || !transaction) throw new AppError(400, "No pending launch in this browser. Launch again.");
    transactions.delete(transactionId);
    reply.clearCookie("smart_launch", cookieOptions);
    const code = api.validateCallback(request.query, transaction);
    const tokens = await api.exchange(config, transaction, code);
    const practitioner = await api.verifyIdentity(config, transaction.discovery, tokens);
    const context = api.launchContext(tokens, practitioner);
    if (sessions.size >= 1000) throw new AppError(503, "Too many sessions; try again later.");
    const oldSession = request.cookies.smart_session;
    if (oldSession) sessions.delete(oldSession);
    const sessionId = random();
    const maxAge = Math.min(tokens.expires_in, 1800);
    sessions.set(sessionId, { tokens, context, expiresAt: Date.now() + maxAge * 1000 });
    reply.setCookie("smart_session", sessionId, { ...cookieOptions, maxAge });
    return reply.redirect("/app");
  });
  app.get("/app", async (request, reply) => {
    const session = sessions.get(request.cookies.smart_session ?? "");
    if (!session) throw new AppError(401, "Session missing or expired. Launch again from the EPJ.");
    const chart = await api.loadChart(config, session.tokens, session.context);
    const rows = Object.entries(chart).map(([key, value]) =>
      `<dt>${escapeHtml(key)}</dt><dd>${escapeHtml(value ?? "Not available")}</dd>`).join("");
    return reply.type("text/html").send(page("Active consultation",
      `<dl>${rows}</dl><h2>Scopes</h2><p>Requested: ${escapeHtml(config.scopes)}</p><p>Granted: ${escapeHtml(session.tokens.scope)}</p>`));
  });
  return app;
}
