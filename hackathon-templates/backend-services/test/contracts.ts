import assert from "node:assert/strict";
import { SignJWT } from "jose";
import { discover, verifyIdentity as verify } from "../src/reference.ts";
import type { fixture } from "./fixture.ts";

type Fixture = Awaited<ReturnType<typeof fixture>>;

export async function checkDiscovery(f: Fixture) {
  const original = structuredClone(f.metadata);
  assert.deepEqual(await discover(f.config), original, "Valid discovery must succeed before checking rejection cases");
  const attacks = [
    { issuer: "https://wrong.invalid/oidc" },
    { authorization_endpoint: "https://wrong.invalid/authorize" },
    { token_endpoint: "https://wrong.invalid/token" },
    { jwks_uri: "https://wrong.invalid/jwks" },
    { token_endpoint_auth_methods_supported: [] },
    { grant_types_supported: [] },
    ...(f.config.variant === "backend-services" ? [] : [{ code_challenge_methods_supported: ["plain"] }]),
  ];
  for (const attack of attacks) {
    Object.assign(f.metadata, original, attack);
    try {
      await assert.rejects(() => discover(f.config), `Discovery must reject ${Object.keys(attack)[0]}`);
    } finally {
      Object.assign(f.metadata, original);
    }
  }
}

export async function checkIdentity(f: Fixture) {
  const claims = {
    iss: f.config.issuer, aud: f.config.clientId, sub: "h1",
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 300, fhirUser: "Practitioner/h1",
  };
  const tokens = { ...await f.tokens(), id_token: await f.signIdentity(claims) };
  assert.equal(await verify(f.config, f.metadata, tokens), "h1", "A valid ID token must succeed");
  await assert.rejects(() => verify(f.config, f.metadata, { ...tokens, id_token: undefined }),
    "Missing ID token must be rejected");
  for (const attack of [
    { aud: "wrong" }, { iss: "https://wrong.invalid" }, { exp: 1 }, { exp: undefined },
    { sub: undefined }, { iat: undefined }, { fhirUser: "https://wrong.invalid/Practitioner/h1" },
    { fhirUser: "Patient/p1" }, { azp: "wrong" },
  ]) {
    const invalid = await f.signIdentity({ ...claims, ...attack });
    await assert.rejects(() => verify(f.config, f.metadata, { ...tokens, id_token: invalid }),
      `ID-token verification must reject ${Object.keys(attack)[0]}`);
  }
  const wrongAlgorithm = await new SignJWT(claims).setProtectedHeader({ alg: "ES384" }).sign(f.client.privateKey);
  await assert.rejects(() => verify(f.config, f.metadata, { ...tokens, id_token: wrongAlgorithm }));
  const signed = tokens.id_token.split(".");
  const signature = Buffer.from(signed[2]!, "base64url");
  signature[0] = signature[0]! ^ 1;
  signed[2] = signature.toString("base64url");
  await assert.rejects(() => verify(f.config, f.metadata, { ...tokens, id_token: signed.join(".") }),
    "Decoding is not verification: a tampered RS256 signature must be rejected");
}
