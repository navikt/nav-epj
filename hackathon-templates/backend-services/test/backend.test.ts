import assert from "node:assert/strict";
import { test } from "node:test";
import { buildApp } from "../src/app.ts";
import * as api from "../src/reference.ts";
import { fixture } from "./fixture.ts";
import { checkDiscovery } from "./contracts.ts";

  test("Reference Backend Services: discovery, exchange and search", async t => {
    const f = await fixture();
    t.after(f.close);
    await t.test("trusted discovery", async () => {
      await checkDiscovery(f);
    });
    await t.test("asymmetric client_credentials exchange", async () => {
      const tokens = await api.backendToken(f.config, f.metadata);
      assert.ok(f.issued.has(tokens.access_token));
      assert.equal(tokens.patient, undefined);
      assert.equal(tokens.refresh_token, undefined);
      assert.ok(tokens.expires_in <= 300);
      assert.equal(f.lastBody().get("grant_type"), "client_credentials");
      assert.equal(f.lastBody().get("scope"), "system/Patient.rs");
      assert.equal(f.lastBody().has("code"), false);
      assert.equal(f.lastBody().has("launch"), false);
    });
    await t.test("identifier-based FHIR search", async () => {
      const patients = await api.backendSearch(f.config, await f.tokens());
      assert.equal(patients[0]?.id, "p1");
    });
  });

test("Reference Backend Services: local page never returns raw tokens", async t => {
  const f = await fixture();
  t.after(f.close);
  const app = await buildApp(f.config, true);
  t.after(() => app.close());
  const response = await app.inject("/app");
  assert.equal(response.statusCode, 200, response.body);
  assert.match(response.body, /Synthetic &lt;Patient&gt;/);
  assert.match(response.body, /system\/Patient.rs/);
  for (const token of f.issued.keys()) assert.equal(response.body.includes(token), false);
  assert.equal((await app.inject("/launch")).statusCode, 404);
});

test("Reference Backend Services: reject patient-bound, long-lived and over-scoped tokens", async t => {
  const f = await fixture();
  t.after(f.close);
  assert.ok(f.issued.has((await api.backendToken(f.config, f.metadata)).access_token));
  for (const override of [
    { patient: "p1" }, { encounter: "e1" }, { refresh_token: "unexpected" },
    { id_token: "unexpected" }, { expires_in: 301 }, { scope: "patient/Patient.r" },
    { scope: "system/Observation.crs" },
  ]) {
    f.overrideTokens(override);
    await assert.rejects(() => api.backendToken(f.config, f.metadata));
  }
});
