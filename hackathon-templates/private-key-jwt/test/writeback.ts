import assert from "node:assert/strict";
import { test } from "node:test";
import * as extensions from "../src/extensions.ts";
import { fixture } from "./fixture.ts";

const context = { patient: "p1", encounter: "e1", practitioner: "h1" };
const date = new Date("2026-01-01T10:00:00Z");

export function writebackTests(type: "Observation" | "DocumentReference") {
  const makeResource = () => type === "Observation" ? extensions.temperature(context, 37.2, date) :
    extensions.journalNote(context, "Synthetic note");

  test(`Reference ${type}: construct the supported resource shape from launch context`, async t => {
    const f = await fixture();
    t.after(f.close);
    const tokens = await f.tokens({ scope: `patient/${type}.cr` });
    const resource = type === "Observation" ? extensions.temperature(context, 37.2, date) :
      extensions.journalNote(context, "Synthetic note");
    assert.equal(resource.resourceType, type);
    assert.equal(resource.subject.reference, "Patient/p1");
    assert.equal("id" in resource, false, "Creation must let the server assign an ID");
    if (resource.resourceType === "Observation") {
      assert.equal(resource.encounter.reference, "Encounter/e1");
      assert.equal(resource.effectiveDateTime, date.toISOString());
      assert.equal(resource.valueQuantity.value, 37.2);
      assert.equal(resource.valueQuantity.code, "Cel");
      assert.throws(() => extensions.temperature(context, Number.NaN, date));
    } else {
      assert.equal(resource.context.encounter[0]?.reference, "Encounter/e1");
      assert.equal(resource.description, "Synthetic note");
      assert.throws(() => extensions.journalNote(context, " "));
    }
    const response = await fetch(`${f.config.fhirBase}/${type}`, {
      method: "POST", headers: { Authorization: `Bearer ${tokens.access_token}`, "Content-Type": "application/fhir+json" },
      body: JSON.stringify(resource),
    });
    assert.equal(response.status, 201, "The fixture validates LOINC/UCUM and nav-epj's narrow document shape");
  });

  test(`Reference ${type}: create, verify Location and read back the persisted resource`, async t => {
    const f = await fixture();
    t.after(f.close);
    const tokens = await f.tokens({ scope: `patient/${type}.cr` });
    const created = await extensions.createResource(f.config, tokens, makeResource());
    assert.equal(created.resourceType, type);
    assert.ok(f.created.has(`${type}/${created.id}`), "Returning a fabricated ID is not a successful write");
    const read = await fetch(`${f.config.fhirBase}/${type}/${created.id}`, {
      headers: { Authorization: `Bearer ${tokens.access_token}` },
    });
    assert.equal(read.status, 200);
    assert.deepEqual(await read.json(), created);
    assert.equal(f.created.size, 1);
  });

  test(`Reference ${type}: reject missing create scope and another patient's subject without writing`, async t => {
    const f = await fixture();
    t.after(f.close);
    const tokens = await f.tokens({ scope: `patient/${type}.cr` });
    await extensions.createResource(f.config, tokens, makeResource());
    assert.equal(f.created.size, 1);
    const readOnly = await f.tokens({ scope: `patient/${type}.r` });
    await assert.rejects(() => extensions.createResource(f.config, readOnly, makeResource()));
    assert.equal(f.requests.filter(r => r.method === "POST" && r.path.endsWith(type)).at(-1)?.status, 403);
    const otherPatient = { ...makeResource(), subject: { reference: "Patient/p2" } };
    await assert.rejects(() => extensions.createResource(f.config, tokens, otherPatient));
    assert.equal(f.requests.filter(r => r.method === "POST" && r.path.endsWith(type)).at(-1)?.status, 404);
    assert.equal(f.created.size, 1, "Rejected writes must not persist anything");
  });

  test(`Reference ${type}: do not report success for a malformed create response`, async t => {
    const f = await fixture();
    t.after(f.close);
    const tokens = await f.tokens({ scope: `patient/${type}.cr` });
    await extensions.createResource(f.config, tokens, makeResource());
    for (const location of ["missing", "wrong"] as const) {
      f.createResponse.location = location;
      await assert.rejects(() => extensions.createResource(f.config, tokens, makeResource()), `Reject ${location} Location`);
    }
    f.createResponse.location = "correct";
    f.createResponse.status = 200;
    await assert.rejects(() => extensions.createResource(f.config, tokens, makeResource()), "FHIR create requires HTTP 201");
    assert.equal(f.created.size, 4, "Do not automatically retry writes with uncertain outcomes");
  });
}
