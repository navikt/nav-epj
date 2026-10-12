import { z } from "zod";
import { variant } from "./variant.ts";

export type Variant = "public" | "client-secret" | "private-key-jwt" | "backend-services";
export interface Config {
  variant: Variant;
  port: number;
  fhirBase: string;
  issuer: string;
  clientId: string;
  redirectUri: string;
  scopes: string;
  secret?: string;
  privateJwkPath?: string;
  patientIdentifier?: string;
}

export class AppError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function unfinished(step: number, task: string): never {
  throw new AppError(501, `TODO ${step}: ${task}. See src/steps.ts and src/reference.ts.`);
}

export function trustedUrl(value: string): URL {
  const url = new URL(value);
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  if ((url.protocol !== "https:" && !(url.protocol === "http:" && loopback)) ||
      url.username || url.password || url.hash || url.search) {
    throw new AppError(400, "Use HTTPS URLs, or HTTP on localhost, without credentials/query/fragment.");
  }
  return url;
}

export function readConfig(env = process.env): Config {
  const values = z.object({
    PORT: z.coerce.number().int().min(1024).max(65535).default(3100),
    FHIR_BASE_URL: z.url(),
    AUTH_ISSUER: z.url(),
    CLIENT_ID: z.string().min(1).refine(v => !v.startsWith("replace-"), "Set your assigned CLIENT_ID"),
    REDIRECT_URI: z.url().default("http://localhost:3100/callback"),
    SCOPES: z.string().trim().min(1),
    CLIENT_SECRET: z.string().min(1).optional(),
    PRIVATE_JWK_PATH: z.string().min(1).optional(),
    PATIENT_IDENTIFIER: z.string().min(1).optional(),
  }).parse(env);
  const fhirBase = trustedUrl(values.FHIR_BASE_URL).href.replace(/\/$/, "");
  const issuer = trustedUrl(values.AUTH_ISSUER).href.replace(/\/$/, "");
  trustedUrl(values.REDIRECT_URI);
  const selected: Variant = variant;
  if (selected === "client-secret" && !values.CLIENT_SECRET) {
    throw new AppError(400, "Set CLIENT_SECRET from your team credential packet.");
  }
  if (selected === "client-secret" &&
      [values.CLIENT_ID, values.CLIENT_SECRET!].some(value => !/^[A-Za-z0-9._~-]+$/.test(value))) {
    throw new AppError(400, "Ask for URL-safe client credentials: nav-epj currently does not form-decode Basic credentials.");
  }
  if ((selected === "private-key-jwt" || selected === "backend-services") && !values.PRIVATE_JWK_PATH) {
    throw new AppError(400, "Set PRIVATE_JWK_PATH to your team's private JWK file.");
  }
  if (selected === "backend-services" &&
      (!values.PATIENT_IDENTIFIER || values.SCOPES.split(/\s+/).some(s => !s.startsWith("system/")))) {
    throw new AppError(400, "Backend Services needs PATIENT_IDENTIFIER and only system scopes.");
  }
  return {
    variant: selected, port: values.PORT, fhirBase, issuer,
    clientId: values.CLIENT_ID, redirectUri: values.REDIRECT_URI, scopes: values.SCOPES,
    secret: values.CLIENT_SECRET, privateJwkPath: values.PRIVATE_JWK_PATH,
    patientIdentifier: values.PATIENT_IDENTIFIER,
  };
}
