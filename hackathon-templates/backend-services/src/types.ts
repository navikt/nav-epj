import { z } from "zod";
import { id } from "./http.ts";

export const discoverySchema = z.object({
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
