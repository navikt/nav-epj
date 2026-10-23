import assert from "node:assert/strict";
import { test } from "node:test";
import * as reference from "../src/reference.ts";
import { fixture } from "./fixture.ts";

test("Reference client authentication: Basic, PKCE and retry after an invalid secret", async t => {
  const f = await fixture();
  t.after(f.close);
  const transaction = reference.newTransaction({ iss: f.config.fhirBase, launch: "launch" }, f.metadata, reference.pkce());
  f.registerCode("valid", transaction.challenge);
  const tokens = await reference.exchange(f.config, transaction, "valid");
  assert.ok(f.issued.has(tokens.access_token));
  assert.equal(f.lastAuthorization(), `Basic ${Buffer.from(`${f.config.clientId}:${f.config.secret}`).toString("base64")}`);
  assert.equal(f.lastBody().has("client_secret"), false, "client_secret_post is not supported");
  assert.equal(f.lastBody().get("code_verifier"), transaction.verifier, "Confidential clients still use PKCE");
  f.registerCode("retry", transaction.challenge);
  const count = f.tokenCalls();
  await assert.rejects(() => reference.exchange({ ...f.config, secret: "incorrect" }, transaction, "retry"));
  assert.equal(f.tokenCalls(), count + 1);
  assert.equal(f.requests.filter(r => r.path === "/oidc/token").at(-1)?.status, 401);
  assert.ok(f.issued.has((await reference.exchange(f.config, transaction, "retry")).access_token),
    "Invalid client authentication must not consume the code");
});

test("Reference client authentication: form-encode special characters in Basic credentials", async t => {
  const f = await fixture();
  t.after(f.close);
  f.config.clientId = "team id/!'()~*+:%";
  f.config.secret = "s3cr:et+/ %!'()~*é";
  const transaction = reference.newTransaction({ iss: f.config.fhirBase, launch: "launch" }, f.metadata, reference.pkce());
  f.registerCode("special", transaction.challenge);
  assert.ok(f.issued.has((await reference.exchange(f.config, transaction, "special")).access_token));
  const encoded = Buffer.from(f.lastAuthorization()!.replace("Basic ", ""), "base64").toString();
  assert.equal(encoded, "team+id%2F%21%27%28%29%7E*%2B%3A%25:s3cr%3Aet%2B%2F+%25%21%27%28%29%7E*%C3%A9");
});
