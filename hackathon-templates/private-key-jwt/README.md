# Key-pair EHR-launch starter

Use the **public/private keys supplied by the host on the day** to build your app and
launch it in nav-epj. The private key signs a client assertion for `private_key_jwt`;
the host registers the corresponding public key. Keep PKCE in the launch flow.

1. Use Node 22 and run `npm ci`.
2. Follow [GUIDE.md](./GUIDE.md). Set your assigned client ID, server addresses and
   `PRIVATE_JWK_PATH` to the host-supplied private JWK.
3. Build your app using `src/steps.ts`; `src/reference.ts` shows a complete implementation.
4. Run `npm start`, log into nav-epj, select a synthetic patient and launch your registered app.

The reference uses **ES384 / EC P-384**. The private JWK needs `alg: ES384`, `use: sig`
and a `kid` matching the registered public JWK. Confirm the key format with the host.
Do not generate replacement keys or commit the supplied private key.

The registration uses `tokenEndpointAuthMethod: private_key_jwt`,
`grantTypes: [authorization_code]`, launch URL `http://localhost:3100/launch`,
redirect URL `http://localhost:3100/callback`, and `launchMode: tab`. The host registers
the **public** JWK Set inline as `jwkSet`; a deployed EPJ cannot reach your laptop's JWKS.

`npm run start:reference` runs the supplied example with the same configuration.
There are no tests for participants to complete. See [EXERCISES.md](./EXERCISES.md)
for feature ideas and [PROGRESSION.md](./PROGRESSION.md) for reusing a working launch.
