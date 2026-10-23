# Backend Services starter

An optional system-to-system app with no EHR launch, clinician, callback or patient context. To
launch your app in nav-epj, use `private-key-jwt/` instead.

1. Use Node 22 and run `npm ci`.
2. Configure it as described in [GUIDE.md](./GUIDE.md). Set `SCOPES` to `system/Patient.rs`,
   `PRIVATE_JWK_PATH` to your private JWK and `PATIENT_IDENTIFIER` to a roster `system|value`.
3. Adapt `src/steps.ts`, run `npm start` and open `http://localhost:3100`.

The host registers `tokenEndpointAuthMethod: private_key_jwt`, `grantTypes: [client_credentials]`,
`scopes: [system/Patient.rs]` and the public JWK Set inline as `jwkSet`, with no launch or redirect
URIs. Do not reuse the EHR client's registration. Each search gets a fresh token with no patient
context and no refresh token. Patient search needs an identifier, so `GET /Patient` does not list the
cohort. Ideas are in [EXERCISES.md](./EXERCISES.md).
