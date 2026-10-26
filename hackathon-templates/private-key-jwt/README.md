# Key-pair EHR-launch starter

Build a SMART app that signs a client assertion with the private key supplied by the host
(`private_key_jwt`). The host registers the public key. PKCE still applies.

1. Use Node 22 and run `npm ci`.
2. Configure it as described in [GUIDE.md](./GUIDE.md), with `PRIVATE_JWK_PATH` pointing to your private JWK.
3. Build your app from `src/steps.ts`, and run `npm start`.

The reference accepts the RSA 3072 / RS384 key from nav-epj or an EC P-384 / ES384 key, with a
matching `alg`, `use: sig` and `kid`. The host registers `tokenEndpointAuthMethod: private_key_jwt`
and `grantTypes: [authorization_code]` with the public JWK Set inline as `jwkSet`, plus the launch and
redirect URLs from the guide. Do not generate replacement keys or commit the private key.
Feature ideas are in [EXERCISES.md](./EXERCISES.md).
