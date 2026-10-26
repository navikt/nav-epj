import Fastify, { LogController } from "fastify";
import type { Config } from "./config.ts";
import { displayName, escapeHtml, page } from "./http.ts";
import * as reference from "./reference.ts";
import { configureServer } from "./server.ts";
import { steps, type Steps } from "./steps.ts";

export async function buildApp(config: Config, referenceMode = false, implementation?: Steps) {
  const app = Fastify({ logger: true, logController: new LogController({ disableRequestLogging: true }) });
  configureServer(app);
  const api = implementation ?? (referenceMode ? reference : steps);
  app.get("/", async (_request, reply) => reply.type("text/html").send(page(
    `SMART Backend Services ${referenceMode ? "reference" : "starter"}`,
    '<p>No clinician login or EHR launch. <a href="/app">Run a system-scoped Patient search</a>.</p>',
  )));
  app.get("/app", async (_request, reply) => {
    const discovery = await api.discover(config);
    const tokens = await api.backendToken(config, discovery);
    const patients = await api.backendSearch(config, tokens);
    const results = patients.map(patient => `<li>${escapeHtml(displayName(patient))}</li>`).join("");
    return reply.type("text/html").send(page("Backend Services result",
      `<p>Granted scopes: ${escapeHtml(tokens.scope)}</p><p>Token lifetime: ${tokens.expires_in} seconds. No launch context.</p>` +
      (patients.length ? `<ul>${results}</ul>` : "<p>No patient matched the supplied identifier.</p>")));
  });
  return app;
}
