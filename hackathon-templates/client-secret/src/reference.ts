import { createHash, randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import { readFile } from "node:fs/promises";
import { createLocalJWKSet, importJWK, jwtVerify, SignJWT } from "jose";
import { z } from "zod";
import { AppError, trustedUrl, type Config } from "./config.ts";
import {
  age, bundle, displayName, encounterSchema, fhir, id, json,
  organizationSchema, patientSchema, practitionerSchema, referenceId, roleSchema,
} from "./http.ts";

const discoverySchema = z.object({
  issuer: z.url(), authorization_endpoint: z.url(), token_endpoint: z.url(), jwks_uri: z.url(),
  token_endpoint_auth_methods_supported: z.array(z.string()),
  code_challenge_methods_supported: z.array(z.string()),
  grant_types_supported: z.array(z.string()),
});
export type Discovery = z.infer<typeof discoverySchema>;
export interface Launch { iss: string; launch: string }
export interface Transaction {
  launch: Launch; discovery: Discovery; state: string;
  verifier: string; challenge: string; createdAt: number;
}
export const tokenSchema = z.object({
  access_token: z.string().min(1), token_type: z.literal("Bearer"),
  expires_in: z.number().int().positive(), scope: z.string(),
  id_token: z.string().optional(), patient: id.optional(), encounter: id.optional(),
  refresh_token: z.string().optional(),
});
export type Tokens = z.infer<typeof tokenSchema>;
export interface Context { patient: string; encounter: string; practitioner: string }
export interface Chart {
  patientName: string; age: number | null; encounter: string; status: string; started: string;
  clinician?: string; organization?: string; practitionerRoles?: number;
}

export function readLaunch(query: unknown, config: Config): Launch {
  const result = z.object({ iss: z.string(), launch: z.string().min(1).max(2048) }).safeParse(query);
  const iss = result.success ? result.data.iss.replace(/\/$/, "") : "";
  if (!result.success || iss !== config.fhirBase) {
    throw new AppError(400, "Launch requires a launch ID and the trusted FHIR issuer.");
  }
  return { iss, launch: result.data.launch };
}

export async function discover(config: Config): Promise<Discovery> {
  const metadata = await json(`${config.fhirBase}/.well-known/smart-configuration`, discoverySchema);
  if (metadata.issuer !== config.issuer) throw new AppError(400, "Unexpected authorization issuer.");
  for (const endpoint of [metadata.authorization_endpoint, metadata.token_endpoint, metadata.jwks_uri]) {
    const url = trustedUrl(endpoint);
    if (url.origin !== new URL(config.issuer).origin) {
      throw new AppError(400, "Discovery endpoint is outside the trusted authorization origin.");
    }
  }
  const method = config.variant === "public" ? "none" :
    config.variant === "client-secret" ? "client_secret_basic" : "private_key_jwt";
  const grant = config.variant === "backend-services" ? "client_credentials" : "authorization_code";
  if (!metadata.token_endpoint_auth_methods_supported.includes(method) ||
      !metadata.grant_types_supported.includes(grant) ||
      (grant === "authorization_code" && !metadata.code_challenge_methods_supported.includes("S256"))) {
    throw new AppError(400, "Server does not advertise the required authentication, grant or PKCE method.");
  }
  return metadata;
}

export function pkce(): { verifier: string; challenge: string } {
  const verifier = randomBytes(32).toString("base64url");
  return { verifier, challenge: createHash("sha256").update(verifier).digest("base64url") };
}

export function newTransaction(launch: Launch, discovery: Discovery, proof: ReturnType<typeof pkce>): Transaction {
  return { launch, discovery, ...proof, state: randomBytes(32).toString("base64url"), createdAt: Date.now() };
}

export function authorizationUrl(config: Config, transaction: Transaction): string {
  const url = new URL(transaction.discovery.authorization_endpoint);
  url.search = new URLSearchParams({
    response_type: "code", client_id: config.clientId, redirect_uri: config.redirectUri,
    scope: config.scopes, aud: transaction.launch.iss, launch: transaction.launch.launch,
    state: transaction.state, code_challenge: transaction.challenge, code_challenge_method: "S256",
  }).toString();
  return url.href;
}

export function validateCallback(query: unknown, transaction: Transaction): string {
  const result = z.object({
    state: z.string().min(1), code: z.string().min(1).optional(), error: z.string().optional(),
  }).safeParse(query);
  const state = result.success ? result.data.state : "";
  const actual = Buffer.from(state);
  const expected = Buffer.from(transaction.state);
  if (!result.success || !state || actual.length !== expected.length ||
      !timingSafeEqual(actual, expected) || Date.now() - transaction.createdAt > 300_000) {
    throw new AppError(400, "Missing, expired or mismatched OAuth state. Launch again from the EPJ.");
  }
  if (result.data.error || !result.data.code) {
    throw new AppError(400, "Authorization was declined or no code was returned. Launch again.");
  }
  return result.data.code;
}

export async function clientAssertion(config: Config, tokenEndpoint: string): Promise<string> {
  if (!config.privateJwkPath) throw new AppError(400, "Missing PRIVATE_JWK_PATH.");
  const base = { kid: z.string().trim().min(1), use: z.literal("sig") };
  const b64 = (min: number, max: number) => z.string().regex(/^[A-Za-z0-9_-]+$/).min(min).max(max);
  const jwk = z.discriminatedUnion("kty", [
    z.object({
      ...base, kty: z.literal("EC"), crv: z.literal("P-384"), alg: z.literal("ES384"),
      x: b64(64, 64), y: b64(64, 64), d: b64(64, 64),
    }),
    z.object({
      ...base, kty: z.literal("RSA"), alg: z.literal("RS384"),
      n: b64(342, 684), e: b64(1, 8), d: b64(342, 684),
      p: b64(1, 400), q: b64(1, 400), dp: b64(1, 400), dq: b64(1, 400), qi: b64(1, 400),
    }),
  ]).parse(JSON.parse(await readFile(config.privateJwkPath, "utf8")));
  const { kid, alg, ...material } = jwk;
  if (jwk.kty === "RSA" && Buffer.from(jwk.n, "base64url").length < 384) {
    throw new AppError(400, "RSA keys must be at least 3072 bits.");
  }
  const key = await importJWK({ ...material, alg }, alg);
  return new SignJWT({})
    .setProtectedHeader({ alg, typ: "JWT", kid })
    .setIssuer(config.clientId).setSubject(config.clientId).setAudience(tokenEndpoint)
    .setIssuedAt().setExpirationTime("2m").setJti(randomUUID()).sign(key);
}

export async function tokenRequest(config: Config, discovery: Discovery, body: URLSearchParams): Promise<Tokens> {
  const headers: Record<string, string> = { "Content-Type": "application/x-www-form-urlencoded" };
  body.set("client_id", config.clientId);
  if (config.variant === "client-secret") {
    if (!config.secret) throw new AppError(400, "Missing CLIENT_SECRET.");
    const formEncode = (s: string) => new URLSearchParams({ v: s }).toString().slice(2);
    headers.Authorization = `Basic ${Buffer.from(`${formEncode(config.clientId)}:${formEncode(config.secret)}`).toString("base64")}`;
  } else if (config.variant !== "public") {
    body.set("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    body.set("client_assertion", await clientAssertion(config, discovery.token_endpoint));
  }
  return json(discovery.token_endpoint, tokenSchema, { method: "POST", headers, body });
}

export function exchange(config: Config, transaction: Transaction, code: string): Promise<Tokens> {
  return tokenRequest(config, transaction.discovery, new URLSearchParams({
    grant_type: "authorization_code", code, code_verifier: transaction.verifier, redirect_uri: config.redirectUri,
  }));
}

export async function verifyIdentity(config: Config, discovery: Discovery, tokens: Tokens): Promise<string> {
  if (!tokens.id_token) throw new AppError(400, "No ID token: request and register openid and fhirUser.");
  const jwks = await json(discovery.jwks_uri, z.object({
    keys: z.array(z.object({ kty: z.string() }).passthrough()),
  }));
  const { payload } = await jwtVerify(tokens.id_token, createLocalJWKSet(jwks), {
    issuer: config.issuer, audience: config.clientId, algorithms: ["RS256"],
    requiredClaims: ["iss", "aud", "sub", "iat", "exp"], clockTolerance: 5,
  });
  if ((Array.isArray(payload.aud) && payload.aud.length > 1 && payload.azp !== config.clientId) ||
      (payload.azp !== undefined && payload.azp !== config.clientId)) {
    throw new AppError(400, "ID token has an unexpected authorized party.");
  }
  // nav-epj signs ID tokens with RS256; ES384 is for client assertions, not ID tokens.
  return referenceId(config, z.string().parse(payload.fhirUser), "Practitioner");
}

export function launchContext(tokens: Tokens, practitioner: string): Context {
  if (!tokens.patient || !tokens.encounter) {
    throw new AppError(400, "Missing patient/encounter launch context. Check the granted launch scope.");
  }
  return { patient: tokens.patient, encounter: tokens.encounter, practitioner: id.parse(practitioner) };
}

export async function loadChart(
  config: Config, tokens: Tokens, context: Context, options: { includeCareTeam?: boolean } = {},
): Promise<Chart> {
  const [patient, encounters] = await Promise.all([
    fhir(config, tokens.access_token, `Patient/${context.patient}`, patientSchema),
    fhir(config, tokens.access_token, `Encounter?${new URLSearchParams({ patient: context.patient })}`, bundle(encounterSchema)),
  ]);
  const encounter = encounters.find(e => e.id === context.encounter);
  if (!encounter || encounter.status !== "in-progress" ||
      referenceId(config, encounter.subject.reference, "Patient") !== context.patient) {
    throw new AppError(400, "The launched encounter is not an active consultation for this patient.");
  }
  const chart: Chart = {
    patientName: displayName(patient), age: age(patient.birthDate),
    encounter: encounter.id, status: encounter.status, started: encounter.period?.start ?? "Not recorded",
  };
  if (options.includeCareTeam) {
    if (!encounter.serviceProvider) {
      throw new AppError(400, "The optional care-team view needs an encounter serviceProvider.");
    }
    const organizationId = referenceId(config, encounter.serviceProvider.reference, "Organization");
    const [practitioner, roles, organization] = await Promise.all([
      fhir(config, tokens.access_token, `Practitioner/${context.practitioner}`, practitionerSchema),
      fhir(config, tokens.access_token, `PractitionerRole?${new URLSearchParams({ practitioner: context.practitioner })}`, bundle(roleSchema)),
      fhir(config, tokens.access_token, `Organization/${organizationId}`, organizationSchema),
    ]);
    Object.assign(chart, {
      clinician: displayName(practitioner), organization: organization.name ?? "Name unavailable",
      practitionerRoles: roles.length,
    });
  }
  return chart;
}

export async function backendToken(config: Config, discovery: Discovery): Promise<Tokens> {
  if (config.variant !== "backend-services") throw new AppError(400, "Use the Backend Services starter.");
  const tokens = await tokenRequest(config, discovery, new URLSearchParams({
    grant_type: "client_credentials", scope: config.scopes,
  }));
  if (tokens.patient || tokens.encounter || tokens.id_token || tokens.refresh_token || tokens.expires_in > 300 ||
      !tokens.scope || tokens.scope.split(/\s+/).some(s => !s.startsWith("system/") || !config.scopes.split(/\s+/).includes(s))) {
    throw new AppError(502, "Backend token violates the patient-less, system-scope, five-minute contract.");
  }
  return tokens;
}

export async function backendSearch(config: Config, tokens: Tokens) {
  if (!config.patientIdentifier) throw new AppError(400, "Set PATIENT_IDENTIFIER from your synthetic cohort roster.");
  return fhir(config, tokens.access_token,
    `Patient?${new URLSearchParams({ identifier: config.patientIdentifier })}`, bundle(patientSchema));
}
