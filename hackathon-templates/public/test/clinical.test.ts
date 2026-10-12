import assert from "node:assert/strict";
import { test } from "node:test";
import * as extensions from "../src/extensions.ts";
import { fixture } from "./fixture.ts";

test("Reference clinical summary: patient/encounter diagnoses and chronological LOINC/UCUM trend", async t => {
  const f = await fixture();
  t.after(f.close);
  const tokens = await f.tokens({ scope: "patient/Condition.s patient/Observation.s" });
  const context = { patient: "p1", encounter: "e1", practitioner: "h1" };
  const result = await extensions.clinicalSummary(f.config, tokens, context, "8310-5");
  assert.equal(result.conditions.length, 1);
  assert.deepEqual(result.measurements.map(m => m.id), ["obs1", "obs2", "obs3"]);
  assert.deepEqual(result.measurements.map(m => m.valueQuantity.value), [36.1, 36.2, 36.3]);
  assert.ok(result.measurements.every(m => m.valueQuantity.system === "http://unitsofmeasure.org"));
  assert.equal(result.trendReady, true);
  const conditionQuery = f.requests.find(r => r.path === "/fhir/Condition")?.query;
  assert.ok(["e1", "Encounter/e1"].includes(conditionQuery?.encounter ?? ""), "Limit diagnoses to the active encounter");
  assert.equal(f.requests.filter(r => r.path === "/fhir/Observation").length, 1);
  f.measurements.push({
    ...f.measurements[0]!, id: "pulse",
    code: { coding: [{ system: "http://loinc.org", code: "8867-4", display: "Heart rate" }] },
    valueQuantity: { value: 80, unit: "beats per minute", system: "http://unitsofmeasure.org", code: "/min" },
  });
  const pulse = await extensions.clinicalSummary(f.config, tokens, context, "8867-4");
  assert.deepEqual(pulse.measurements.map(m => m.id), ["pulse"], "Use the requested LOINC code, not a hardcoded one");
  assert.equal(pulse.trendReady, false);
  f.measurements.splice(1);
  const short = await extensions.clinicalSummary(f.config, tokens, context, "8310-5");
  assert.equal(short.trendReady, false, "One measurement is not a trend");
  f.measurements.splice(0);
  const empty = await extensions.clinicalSummary(f.config, tokens, context, "8310-5");
  assert.equal(empty.trendReady, false);
  assert.deepEqual(empty.measurements, []);
});

test("Reference clinical summary: never combine incompatible units into a trend", async t => {
  const f = await fixture();
  t.after(f.close);
  const tokens = await f.tokens({ scope: "patient/Condition.s patient/Observation.s" });
  const context = { patient: "p1", encounter: "e1", practitioner: "h1" };
  await extensions.clinicalSummary(f.config, tokens, context);
  f.measurements[0]!.valueQuantity.code = "[degF]";
  await assert.rejects(() => extensions.clinicalSummary(f.config, tokens, context), "Mixed units must not be silently compared");
});
