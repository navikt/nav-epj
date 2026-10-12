import assert from "node:assert/strict";
import { test } from "node:test";
import { SignJWT } from "jose";
import { fixture } from "./fixture.ts";
import { checkDiscovery, checkIdentity } from "./contracts.ts";
import * as reference from "../src/reference.ts";

test("Reference: discovery trust and ID-token verification reject invalid inputs", async t => {
  const f = await fixture();
  t.after(f.close);
  await checkDiscovery(f);
  await checkIdentity(f);
});

test("Scaffolding: malformed FHIR search queries never return successful bundles", async t => {
  const f = await fixture();
  t.after(f.close);
  const token = await f.tokens({ scope: "system/Patient.s system/Encounter.s system/PractitionerRole.s system/Condition.s system/Observation.s" });
  for (const path of [
    "Patient?foo=bar", "Encounter?foo=bar", "Encounter?patient=p2",
    "PractitionerRole?foo=bar", "PractitionerRole?practitioner=wrong",
    "Condition?foo=bar", "Condition?subject=p1&encounter=wrong",
    "Observation?foo=bar", "Observation?subject=p1&code=wrong",
  ]) {
    const result = await fetch(`${f.config.fhirBase}/${path}`, { headers: { Authorization: `Bearer ${token.access_token}` } });
    assert.equal(result.status, 400, path);
    assert.equal((await result.json()).resourceType, "OperationOutcome");
  }
});

test("Scaffolding: assertion attacks return invalid_client rather than an accidental server error", async t => {
  const f = await fixture();
  t.after(f.close);
  // Exercise assertion verification even when maintaining the public starter's shared fixture.
  f.config.variant = "private-key-jwt";
  const proof = reference.pkce();
  f.registerCode("assertion-code", proof.challenge);
  const claims = { iss: f.config.clientId, sub: f.config.clientId, aud: f.metadata.token_endpoint,
    exp: Math.floor(Date.now() / 1000) + 120, jti: "fixture-jti" };
  const post = async (assertion: string) => fetch(f.metadata.token_endpoint, {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: f.config.clientId, grant_type: "authorization_code", code: "assertion-code",
      redirect_uri: f.config.redirectUri, code_verifier: proof.verifier,
      client_assertion_type: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", client_assertion: assertion,
    }),
  });
  for (const attack of [
    { aud: "wrong" }, { exp: 1 }, { exp: Math.floor(Date.now() / 1000) + 600 }, { iss: "wrong" },
  ]) {
    const assertion = await new SignJWT({ ...claims, ...attack })
      .setProtectedHeader({ alg: "ES384", kid: "test-key", typ: "JWT" }).sign(f.client.privateKey);
    const result = await post(assertion);
    assert.equal(result.status, 401);
    assert.deepEqual(await result.json(), { error: "invalid_client" });
  }
  const valid = await reference.clientAssertion(f.config, f.metadata.token_endpoint);
  assert.equal((await post(valid)).status, 200, "Invalid client assertions must not consume the code");
  const replay = await post(valid);
  assert.equal(replay.status, 401);
  assert.deepEqual(await replay.json(), { error: "invalid_client" });
});
