import assert from "node:assert/strict";
import { test } from "node:test";
import { jwtVerify } from "jose";
import * as reference from "../src/reference.ts";
import { fixture } from "./fixture.ts";

const keys: [alg: "ES384" | "RS384", modulusLength?: number][] = [["ES384"], ["RS384"], ["RS384", 2048]];
for (const [alg, modulusLength] of keys) test(`Reference client authentication (${alg}${modulusLength ? ` ${modulusLength}` : ""}): correctly signed, short-lived, fresh assertions`, async t => {
  const f = await fixture(alg, modulusLength);
  t.after(f.close);
  const transaction = reference.newTransaction({ iss: f.config.fhirBase, launch: "launch" }, f.metadata, reference.pkce());
  const identifiers = new Set();
  for (const code of ["first", "second"]) {
    f.registerCode(code, transaction.challenge);
    assert.ok(f.issued.has((await reference.exchange(f.config, transaction, code)).access_token));
    const assertion = f.lastBody().get("client_assertion");
    assert.ok(assertion);
    assert.equal(f.lastAuthorization(), undefined);
    assert.equal(f.lastBody().get("client_assertion_type"), "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    const { payload, protectedHeader } = await jwtVerify(assertion, f.client.publicKey, {
      issuer: f.config.clientId, subject: f.config.clientId, audience: f.metadata.token_endpoint, algorithms: [alg],
    });
    assert.equal(protectedHeader.alg, alg);
    assert.equal(protectedHeader.kid, "test-key");
    assert.equal(protectedHeader.typ, "JWT");
    assert.equal(typeof payload.jti, "string");
    assert.ok(payload.exp! > Math.floor(Date.now() / 1000));
    assert.ok(payload.exp! <= Math.floor(Date.now() / 1000) + 300);
    assert.equal(identifiers.has(payload.jti), false, "Never reuse a client assertion");
    identifiers.add(payload.jti);
    assert.equal(f.lastBody().get("code_verifier"), transaction.verifier);
  }
});
