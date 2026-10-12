import { createHash, randomUUID } from "node:crypto";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify, { type FastifyReply } from "fastify";
import { errors, exportJWK, generateKeyPair, jwtVerify, SignJWT, type JWTPayload } from "jose";
import { z } from "zod";
import type { Config } from "../src/config.ts";
import { variant } from "../src/variant.ts";
import type { Discovery, Tokens } from "../src/reference.ts";
import type { encounterSchema } from "../src/http.ts";

const reference = z.object({ reference: z.string() }).strict();
const measurementInput = z.object({
  resourceType: z.literal("Observation"), id: z.string().optional(), status: z.literal("final"),
  code: z.object({ coding: z.array(z.object({
    system: z.literal("http://loinc.org"), code: z.literal("8310-5"), display: z.string().min(1),
  }).strict()).length(1) }).strict(),
  subject: reference, encounter: reference, effectiveDateTime: z.iso.datetime({ offset: true }),
  valueQuantity: z.object({
    value: z.number().finite(), unit: z.string().min(1),
    system: z.literal("http://unitsofmeasure.org"), code: z.literal("Cel"),
  }).strict(),
}).strict();
const documentInput = z.object({
  resourceType: z.literal("DocumentReference"), id: z.string().optional(), status: z.literal("current"),
  type: z.object({ coding: z.array(z.object({
    system: z.literal("urn:oid:2.16.578.1.12.4.1.1.9602"), code: z.literal("J01-2"),
  }).strict()).length(1) }).strict(),
  subject: reference, context: z.object({ encounter: z.array(reference).length(1) }).strict(),
  description: z.string().trim().min(1),
  content: z.array(z.object({
    attachment: z.object({ contentType: z.literal("application/pdf") }).strict(),
  }).strict()).length(1),
}).strict();

export async function fixture() {
  const server = Fastify();
  const signing = await generateKeyPair("RS256");
  const client = await generateKeyPair("ES384", { extractable: true });
  const directory = await mkdtemp(join(tmpdir(), "smart-starter-test-"));
  const keyPath = join(directory, "private.json");
  await writeFile(keyPath, JSON.stringify({
    ...await exportJWK(client.privateKey), kid: "test-key", alg: "ES384", use: "sig",
  }), { mode: 0o600 });
  const config: Config = {
    variant, port: 3100, fhirBase: "", issuer: "", clientId: "test-team",
    redirectUri: "http://localhost:3100/callback", secret: "synthetic-test-secret",
    privateJwkPath: keyPath, patientIdentifier: "urn:oid:2.16.578.1.12.4.1.4.1|synthetic",
    scopes: variant === "backend-services" ? "system/Patient.rs" :
      "openid fhirUser launch patient/Patient.r patient/Encounter.s",
  };
  const registeredScopes = config.scopes.split(" ");
  const metadata: Discovery = {
    issuer: "", authorization_endpoint: "", token_endpoint: "", jwks_uri: "",
    token_endpoint_auth_methods_supported: ["none", "client_secret_basic", "private_key_jwt"],
    code_challenge_methods_supported: ["S256"], grant_types_supported: ["authorization_code", "client_credentials"],
  };
  const issued = new Map<string, { scopes: string[]; expiresAt: number; patient?: string }>();
  const requests: { path: string; query: Record<string, string>; method: string; status: number }[] = [];
  const created = new Map<string, Record<string, unknown>>();
  const createResponse = { status: 201, location: "correct" as "correct" | "missing" | "wrong" };
  let tokenCalls = 0;
  let lastBody = new URLSearchParams();
  let lastAuthorization: string | undefined;
  let tokenOverrides: Partial<Tokens> = {};
  const usedAssertions = new Set<string>();
  const codes = new Map<string, { challenge: string; scope: string }>();
  const patient = { resourceType: "Patient", id: "p1", name: [{ given: ["Synthetic"], family: "<Patient>" }], birthDate: "1990-06-15" };
  const encounter: z.infer<typeof encounterSchema> = {
    resourceType: "Encounter", id: "e1", status: "in-progress",
    subject: { reference: "Patient/p1" }, serviceProvider: { reference: "Organization/o1" },
    period: { start: "2026-01-01T10:00:00Z" },
  };
  const measurements = [3, 1, 2].map(n => ({
    resourceType: "Observation", id: `obs${n}`, effectiveDateTime: `2026-01-0${n}T10:00:00Z`,
    code: { coding: [{ system: "http://loinc.org", code: "8310-5", display: "Body temperature" }] },
    subject: { reference: "Patient/p1" }, encounter: { reference: "Encounter/e1" },
    valueQuantity: { value: 36 + n / 10, unit: "degrees Celsius", system: "http://unitsofmeasure.org", code: "Cel" },
  }));
  const searchset = (resources: unknown[]) => ({ resourceType: "Bundle", type: "searchset", entry: resources.map(resource => ({ resource })) });
  const outcome = (reply: FastifyReply, status: number, code: string) =>
    reply.code(status).send({ resourceType: "OperationOutcome", issue: [{ severity: "error", code }] });
  const tokens = async (overrides: Partial<Tokens> = {}): Promise<Tokens> => {
    const result: Tokens = {
      access_token: `synthetic-${randomUUID()}`, token_type: "Bearer", scope: config.scopes, expires_in: 300,
      ...(config.variant === "backend-services" ? {} : {
        patient: "p1", encounter: "e1",
        id_token: await new SignJWT({ fhirUser: "Practitioner/h1" })
          .setProtectedHeader({ alg: "RS256" }).setIssuer(config.issuer).setAudience(config.clientId)
          .setSubject("h1").setIssuedAt().setExpirationTime("5m").sign(signing.privateKey),
      }),
      ...overrides, ...tokenOverrides,
    };
    issued.set(result.access_token, {
      scopes: result.scope.split(/\s+/), expiresAt: Date.now() + result.expires_in * 1000, patient: result.patient,
    });
    return result;
  };
  server.addContentTypeParser("application/x-www-form-urlencoded", { parseAs: "string" },
    (_req, body, done) => done(null, new URLSearchParams(String(body))));
  server.addHook("onResponse", async (request, reply) => {
    const url = new URL(request.url, "http://localhost");
    requests.push({ path: url.pathname, query: Object.fromEntries(url.searchParams), method: request.method, status: reply.statusCode });
  });
  server.get("/fhir/.well-known/smart-configuration", async () => metadata);
  server.get("/oidc/jwks", async () => ({ keys: [await exportJWK(signing.publicKey)] }));
  server.post<{ Body: URLSearchParams }>("/oidc/token", async (request, reply) => {
    tokenCalls++;
    lastBody = request.body;
    lastAuthorization = request.headers.authorization;
    const invalidClient = () => reply.code(401).send({ error: "invalid_client" });
    if (lastBody.get("client_id") !== config.clientId) return invalidClient();
    if (config.variant === "public" && (lastAuthorization || lastBody.has("client_assertion") || lastBody.has("client_secret"))) return invalidClient();
    if (config.variant === "client-secret" &&
        (lastAuthorization !== `Basic ${Buffer.from(`${config.clientId}:${config.secret}`).toString("base64")}` ||
         lastBody.has("client_secret"))) return invalidClient();
    if (config.variant === "private-key-jwt" || config.variant === "backend-services") {
      if (lastBody.get("client_assertion_type") !== "urn:ietf:params:oauth:client-assertion-type:jwt-bearer") return invalidClient();
      try {
        const { payload, protectedHeader } = await jwtVerify(lastBody.get("client_assertion") ?? "", client.publicKey, {
          issuer: config.clientId, subject: config.clientId, audience: metadata.token_endpoint,
          algorithms: ["ES384"], requiredClaims: ["iss", "sub", "aud", "exp", "jti"],
        });
        if (protectedHeader.kid !== "test-key" || protectedHeader.typ !== "JWT" ||
            !payload.jti || usedAssertions.has(payload.jti) ||
            !payload.exp || payload.exp > Math.floor(Date.now() / 1000) + 300) return invalidClient();
        usedAssertions.add(payload.jti);
      } catch (error) {
        if (!(error instanceof errors.JOSEError)) throw error;
        return invalidClient();
      }
    }
    if (lastBody.get("grant_type") === "authorization_code" && config.variant !== "backend-services") {
      const code = lastBody.get("code") ?? "";
      const authorization = codes.get(code);
      codes.delete(code);
      const proof = createHash("sha256").update(lastBody.get("code_verifier") ?? "").digest("base64url");
      if (!authorization || authorization.challenge !== proof || lastBody.get("redirect_uri") !== config.redirectUri) {
        return reply.code(400).send({ error: "invalid_grant" });
      }
      return tokens({ scope: authorization.scope });
    }
    if (lastBody.get("grant_type") !== "client_credentials" || config.variant !== "backend-services") {
      return reply.code(400).send({ error: "unsupported_grant_type" });
    }
    const requested = (lastBody.get("scope") ?? "").split(/\s+/);
    if (requested.some(scope => !registeredScopes.includes(scope))) return reply.code(400).send({ error: "invalid_scope" });
    return tokens({ scope: requested.join(" ") });
  });
  server.addHook("preHandler", async (request, reply) => {
    if (!request.url.startsWith("/fhir/") || request.url.includes(".well-known")) return;
    const token = issued.get(request.headers.authorization?.replace(/^Bearer /, "") ?? "");
    if (!token || token.expiresAt <= Date.now() || !request.headers.authorization?.startsWith("Bearer ")) {
      return outcome(reply, 401, "login");
    }
    const parts = new URL(request.url, "http://localhost").pathname.split("/");
    const resource = parts[2];
    const interaction = request.method === "POST" ? "c" : parts.length === 3 ? "s" : "r";
    const system = token.scopes.some(s => s.startsWith("system/"));
    // Match nav-epj's self-practitioner and non-system organization read exceptions.
    if (interaction === "r" && resource === "Practitioner" && parts[3] === "h1" && token.scopes.includes("fhirUser")) return;
    if (interaction === "r" && resource === "Organization" && !system) return;
    const prefix = `${system ? "system" : "patient"}/${resource}.`;
    if (!token.scopes.some(s => s.startsWith(prefix) && s.slice(prefix.length).includes(interaction))) {
      return outcome(reply, 403, "forbidden");
    }
  });
  const query = (url: string) => new URL(url, "http://localhost").searchParams;
  const patientQuery = (params: URLSearchParams) => {
    const values = [...params.getAll("patient"), ...params.getAll("subject")];
    return values.length === 1 && ["p1", "Patient/p1"].includes(values[0]!);
  };
  const allowedQuery = (params: URLSearchParams, keys: string[]) =>
    [...params.keys()].every(key => keys.includes(key) && params.getAll(key).length === 1);
  server.get("/fhir/Patient/p1", async () => patient);
  server.get("/fhir/Patient", async (request, reply) => {
    const params = query(request.url);
    if (!allowedQuery(params, ["identifier"]) || params.get("identifier") !== config.patientIdentifier) return outcome(reply, 400, "invalid");
    return searchset([patient]);
  });
  server.get("/fhir/Encounter", async (request, reply) => {
    const params = query(request.url);
    if (!allowedQuery(params, ["patient", "subject"]) || !patientQuery(params)) return outcome(reply, 400, "invalid");
    return searchset([encounter]);
  });
  server.get("/fhir/Practitioner/h1", async () => ({ resourceType: "Practitioner", id: "h1", name: [{ text: "Test clinician" }] }));
  server.get("/fhir/PractitionerRole", async (request, reply) => {
    const params = query(request.url);
    if (!allowedQuery(params, ["practitioner"]) || !["h1", "Practitioner/h1"].includes(params.get("practitioner") ?? "")) return outcome(reply, 400, "invalid");
    return searchset([{ resourceType: "PractitionerRole", id: "r1" }]);
  });
  server.get("/fhir/Organization/o1", async () => ({ resourceType: "Organization", id: "o1", name: "Test practice" }));
  server.get("/fhir/Condition", async (request, reply) => {
    const params = query(request.url);
    if (!allowedQuery(params, ["patient", "subject", "encounter"]) || !patientQuery(params) ||
        (params.has("encounter") && !["e1", "Encounter/e1"].includes(params.get("encounter")!))) return outcome(reply, 400, "invalid");
    return searchset([{
      resourceType: "Condition", id: "c1", subject: { reference: "Patient/p1" },
      encounter: { reference: "Encounter/e1" }, code: { coding: [{ system: "urn:oid:2.16.578.1.12.4.1.1.7170", code: "R05" }] },
    }]);
  });
  server.get("/fhir/Observation", async (request, reply) => {
    const params = query(request.url);
    const code = params.get("code")?.replace(/^http:\/\/loinc.org\|/, "");
    if (!allowedQuery(params, ["patient", "subject", "code"]) || !patientQuery(params) ||
        !["8310-5", "8867-4"].includes(code ?? "")) return outcome(reply, 400, "invalid");
    return searchset(measurements.filter(m => m.code.coding.some(coding => coding.code === code)));
  });
  server.addContentTypeParser("application/fhir+json", { parseAs: "string" }, server.getDefaultJsonParser("error", "error"));
  for (const type of ["Observation", "DocumentReference"]) {
    server.post(`/fhir/${type}`, async (request, reply) => {
      const parsed = (type === "Observation" ? measurementInput : documentInput).safeParse(request.body);
      if (!parsed.success) return outcome(reply, 400, "invalid");
      const body = parsed.data;
      const token = issued.get(request.headers.authorization!.slice(7))!;
      if (token.patient && body.subject.reference !== `Patient/${token.patient}`) return outcome(reply, 404, "not-found");
      const encounterRef = body.resourceType === "Observation" ? body.encounter.reference : body.context.encounter[0]!.reference;
      if (body.subject.reference !== "Patient/p1" || encounterRef !== "Encounter/e1") return outcome(reply, 400, "invalid");
      const resource = { ...body, id: `created-${created.size + 1}` };
      created.set(`${type}/${resource.id}`, resource);
      if (createResponse.location !== "missing") {
        reply.header("Location", `${config.fhirBase}/${type}/${createResponse.location === "correct" ? resource.id : "wrong-id"}`);
      }
      return reply.code(createResponse.status).send(resource);
    });
    server.get<{ Params: { id: string } }>(`/fhir/${type}/:id`, async (request, reply) => {
      const resource = created.get(`${type}/${request.params.id}`);
      return resource ?? outcome(reply, 404, "not-found");
    });
  }
  const origin = await server.listen({ port: 0, host: "127.0.0.1" });
  config.fhirBase = `${origin}/fhir`;
  config.issuer = `${origin}/oidc`;
  Object.assign(metadata, {
    issuer: config.issuer, authorization_endpoint: `${config.issuer}/authorize`,
    token_endpoint: `${config.issuer}/token`, jwks_uri: `${config.issuer}/jwks`,
  });
  return {
    config, metadata, patient, encounter, measurements, tokens, client, issued, requests, created, createResponse,
    registerCode: (code: string, challenge: string, scope = config.scopes) =>
      codes.set(code, { challenge, scope: scope.split(/\s+/).filter(s => registeredScopes.includes(s)).join(" ") }),
    tokenCalls: () => tokenCalls, lastBody: () => lastBody, lastAuthorization: () => lastAuthorization,
    overrideTokens: (values: Partial<Tokens>) => { tokenOverrides = values; },
    signIdentity: (claims: JWTPayload) => new SignJWT(claims).setProtectedHeader({ alg: "RS256" }).sign(signing.privateKey),
    close: async () => { await server.close(); await rm(directory, { recursive: true, force: true }); },
  };
}
