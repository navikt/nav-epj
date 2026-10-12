import { z } from "zod";
import { AppError, type Config } from "./config.ts";
import { bundle, fhir, fhirUrl, id, referenceId, request } from "./http.ts";
import type { Context, Tokens } from "./reference.ts";

const condition = z.object({ resourceType: z.literal("Condition") }).passthrough();
const observation = z.object({
  resourceType: z.literal("Observation"), id,
  effectiveDateTime: z.iso.datetime({ offset: true }),
  valueQuantity: z.object({
    value: z.number().finite(), unit: z.string(),
    system: z.literal("http://unitsofmeasure.org"), code: z.string(),
  }),
});

export async function clinicalSummary(config: Config, tokens: Tokens, context: Context, loinc = "8310-5") {
  const [conditions, measurements] = await Promise.all([
    fhir(config, tokens.access_token,
      `Condition?${new URLSearchParams({ subject: context.patient, encounter: context.encounter })}`, bundle(condition)),
    fhir(config, tokens.access_token,
      `Observation?${new URLSearchParams({ subject: context.patient, code: `http://loinc.org|${loinc}` })}`, bundle(observation)),
  ]);
  const units = new Set(measurements.map(m => m.valueQuantity.code));
  if (units.size > 1) throw new AppError(400, "Mixed units: convert explicitly before presenting a trend.");
  return {
    conditions,
    measurements: measurements.sort((a, b) => Date.parse(a.effectiveDateTime) - Date.parse(b.effectiveDateTime)),
    trendReady: measurements.length >= 3,
  };
}

export function temperature(context: Context, celsius: number, effective = new Date()) {
  z.number().finite().parse(celsius);
  return {
    resourceType: "Observation" as const, status: "final",
    code: { coding: [{ system: "http://loinc.org", code: "8310-5", display: "Body temperature" }] },
    subject: { reference: `Patient/${id.parse(context.patient)}` },
    encounter: { reference: `Encounter/${id.parse(context.encounter)}` },
    effectiveDateTime: effective.toISOString(),
    valueQuantity: { value: celsius, unit: "degrees Celsius", system: "http://unitsofmeasure.org", code: "Cel" },
  };
}

export function journalNote(context: Context, text: string) {
  return {
    resourceType: "DocumentReference" as const, status: "current",
    type: { coding: [{ system: "urn:oid:2.16.578.1.12.4.1.1.9602", code: "J01-2" }] },
    subject: { reference: `Patient/${id.parse(context.patient)}` },
    context: { encounter: [{ reference: `Encounter/${id.parse(context.encounter)}` }] },
    description: z.string().trim().min(1).parse(text),
    // nav-epj stores the description, not an uploaded PDF. Its write contract rejects attachment.data.
    content: [{ attachment: { contentType: "application/pdf" } }],
  };
}

export async function createResource(
  config: Config, tokens: Tokens, resource: ReturnType<typeof temperature> | ReturnType<typeof journalNote>,
) {
  const response = await request(fhirUrl(config, resource.resourceType), {
    method: "POST",
    headers: { Authorization: `Bearer ${tokens.access_token}`, "Content-Type": "application/fhir+json", Accept: "application/fhir+json" },
    body: JSON.stringify(resource),
  });
  const created = z.object({ resourceType: z.string(), id }).passthrough().parse(await response.json());
  const location = response.headers.get("Location");
  if (response.status !== 201 || created.resourceType !== resource.resourceType || !location ||
      referenceId(config, location, resource.resourceType) !== created.id) {
    throw new AppError(502, "Create response must be 201 with a server ID and matching Location.");
  }
  return created;
}
