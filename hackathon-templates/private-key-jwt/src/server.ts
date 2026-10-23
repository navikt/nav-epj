import type { FastifyInstance } from "fastify";
import { AppError } from "./config.ts";
import { escapeHtml, page } from "./http.ts";

export function configureServer(app: FastifyInstance): void {
  app.addHook("onSend", async (_request, reply) => {
    reply.header("Cache-Control", "no-store");
    reply.header("Referrer-Policy", "same-origin");
    reply.header("X-Content-Type-Options", "nosniff");
    reply.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'");
  });
  app.setErrorHandler((error, request, reply) => {
    // Fastify request logging is disabled so callback codes never reach access logs.
    request.log.warn({ category: error instanceof Error ? error.name : "UnknownError" }, "SMART request failed");
    const status = error instanceof AppError ? error.status : 502;
    const message = error instanceof AppError ? error.message :
      "Request failed validation, token verification or upstream communication. Check registration and try a new launch.";
    return reply.code(status).type("text/html").send(page("SMART request failed", `<p>${escapeHtml(message)}</p>`));
  });
}
