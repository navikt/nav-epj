import assert from "node:assert/strict";
import { test } from "node:test";
import { age, bundle, fhirUrl, patientSchema, referenceId } from "../src/http.ts";
import { fixture } from "./fixture.ts";
import { readConfig } from "../src/config.ts";

test("Scaffolding: host configuration is validated without event access", async t => {
  const f = await fixture();
  t.after(f.close);
  const env = {
    FHIR_BASE_URL: f.config.fhirBase, AUTH_ISSUER: f.config.issuer, CLIENT_ID: f.config.clientId,
    SCOPES: f.config.scopes, CLIENT_SECRET: f.config.secret, PRIVATE_JWK_PATH: f.config.privateJwkPath,
    PATIENT_IDENTIFIER: f.config.patientIdentifier,
  };
  const config = readConfig(env);
  assert.equal(config.variant, f.config.variant);
  assert.equal(config.port, 3100);
  assert.throws(() => readConfig({ ...env, CLIENT_ID: "" }));
  assert.throws(() => readConfig({ ...env, FHIR_BASE_URL: "http://insecure.example/fhir" }));
  if (config.variant === "client-secret") {
    assert.throws(() => readConfig({ ...env, CLIENT_SECRET: undefined }));
  }
  if (config.variant === "private-key-jwt" || config.variant === "backend-services") {
    assert.throws(() => readConfig({ ...env, PRIVATE_JWK_PATH: undefined }));
  }
});

test("Scaffolding: FHIR URLs cannot leak bearer tokens to another origin or path", async t => {
  const f = await fixture();
  t.after(f.close);
  for (const path of ["https://wrong.invalid/Patient/p1", "//wrong.invalid/p1", "../oidc/token", "/other/Patient/p1"]) {
    assert.throws(() => fhirUrl(f.config, path));
  }
  assert.equal(referenceId(f.config, `${f.config.fhirBase}/Practitioner/h1`, "Practitioner"), "h1");
  assert.throws(() => referenceId(f.config, "Patient/p1", "Practitioner"));
});

test("Scaffolding: age boundaries and incomplete FHIR data are honest", () => {
  assert.equal(age("1990-06-15", new Date("2026-06-14T12:00:00Z")), 35);
  assert.equal(age("1990-06-15", new Date("2026-06-15T12:00:00Z")), 36);
  assert.equal(age("1990-06-15", new Date("2026-06-14T22:30:00Z")), 36, "Oslo is already on June 15");
  assert.equal(age("1990-06-15", new Date("2026-06-14T21:30:00Z")), 35);
  assert.equal(age("1990"), null);
  assert.equal(age(undefined), null);
  assert.throws(() => age("2026-02-30"));
  assert.deepEqual(bundle(patientSchema).parse({ resourceType: "Bundle", type: "searchset" }), []);
  assert.throws(() => bundle(patientSchema).parse({
    resourceType: "Bundle", type: "searchset", link: [{ relation: "next", url: "https://example.invalid" }],
  }), /pagination/);
});
