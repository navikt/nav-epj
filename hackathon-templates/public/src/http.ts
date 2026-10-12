import { z } from "zod";
import { AppError, type Config } from "./config.ts";

export async function request(url: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(url, {
    ...init, redirect: "error", signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    // Never include upstream bodies: they can contain credentials or patient data.
    throw new AppError(502, `Upstream returned HTTP ${response.status}. Check scopes, registration and mentor logs.`);
  }
  return response;
}

export async function json<T>(url: string, schema: z.ZodType<T>, init?: RequestInit): Promise<T> {
  return schema.parse(await (await request(url, init)).json());
}

export const id = z.string().regex(/^[A-Za-z0-9.-]{1,64}$/);
export const referenceSchema = z.object({ reference: z.string().min(1) });
const names = z.array(z.object({
  text: z.string().optional(), given: z.array(z.string()).optional(), family: z.string().optional(),
})).default([]);
export const patientSchema = z.object({
  resourceType: z.literal("Patient"), id, name: names, birthDate: z.string().optional(),
});
export const practitionerSchema = z.object({
  resourceType: z.literal("Practitioner"), id, name: names,
});
export const encounterSchema = z.object({
  resourceType: z.literal("Encounter"), id, status: z.string(),
  subject: referenceSchema, serviceProvider: referenceSchema.optional(),
  period: z.object({ start: z.string().optional(), end: z.string().optional() }).optional(),
});
export const organizationSchema = z.object({
  resourceType: z.literal("Organization"), id, name: z.string().optional(),
});
export const roleSchema = z.object({
  resourceType: z.literal("PractitionerRole"), id: id.optional(),
}).passthrough();
export function bundle<T>(resource: z.ZodType<T>) {
  return z.object({
    resourceType: z.literal("Bundle"), type: z.literal("searchset"),
    entry: z.array(z.object({ resource })).default([]),
    link: z.array(z.object({ relation: z.string(), url: z.string() })).optional(),
  }).transform(b => {
    if (b.link?.some(link => link.relation === "next")) {
      throw new AppError(502, "This starter does not implement pagination; do not present a partial result as complete.");
    }
    return b.entry.map(entry => entry.resource);
  });
}

export function fhirUrl(config: Config, path: string): string {
  const base = new URL(`${config.fhirBase}/`);
  const url = new URL(path, base);
  if (url.origin !== base.origin || !url.pathname.startsWith(base.pathname) ||
      url.username || url.password || url.hash) {
    throw new AppError(400, "FHIR reference points outside the configured FHIR server.");
  }
  return url.href;
}

export function referenceId(config: Config, value: string, type: string): string {
  const url = new URL(fhirUrl(config, value));
  const prefix = `${new URL(config.fhirBase).pathname}/${type}/`;
  if (!url.pathname.startsWith(prefix) || url.search) {
    throw new AppError(400, `Expected a ${type} reference.`);
  }
  return id.parse(url.pathname.slice(prefix.length));
}

export function fhir<T>(config: Config, token: string, path: string, schema: z.ZodType<T>): Promise<T> {
  return json(fhirUrl(config, path), schema, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/fhir+json" },
  });
}

export function displayName(resource: { name: z.infer<typeof names> }): string {
  const name = resource.name[0];
  return name?.text || [...(name?.given ?? []), name?.family].filter(Boolean).join(" ") || "Name unavailable";
}

export function age(birthDate: string | undefined, today = new Date()): number | null {
  if (!birthDate || !/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) return null;
  const birth = new Date(`${birthDate}T00:00:00Z`);
  if (!Number.isFinite(birth.getTime()) || birth.toISOString().slice(0, 10) !== birthDate || birth > today) {
    throw new AppError(502, "Patient has an invalid birth date.");
  }
  const beforeBirthday = today.toISOString().slice(5, 10) < birthDate.slice(5);
  return today.getUTCFullYear() - birth.getUTCFullYear() - Number(beforeBirthday);
}

export function escapeHtml(value: unknown): string {
  return String(value).replace(/[&<>"']/g, char =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]!);
}

export function page(title: string, content: string): string {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${escapeHtml(title)}</title></head><body><main><h1>${escapeHtml(title)}</h1>${content}</main></body></html>`;
}
