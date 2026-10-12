# Backend Services starter

An optional system-to-system app: **no EHR launch, clinician login, callback or patient
launch context**. If your goal is to launch your app in nav-epj with the supplied key pair,
use `private-key-jwt/` instead.

This variant authenticates with a signed client assertion and requests `client_credentials`
with pre-authorized `system/*` scopes.

1. Use Node 22 and run `npm ci`.
2. Follow [GUIDE.md](./GUIDE.md). The host supplies a dedicated backend client ID,
   public/private keys and a synthetic patient identifier. The reference expects an ES384
   private JWK at `PRIVATE_JWK_PATH`.
3. Set `SCOPES` to `system/Patient.rs` and `PATIENT_IDENTIFIER` to the roster's
   `identifier-system|identifier-value`.
4. Adapt `src/steps.ts`, run `npm start`, and open `http://localhost:3100` to use your app.

The host registers `tokenEndpointAuthMethod: private_key_jwt`,
`grantTypes: [client_credentials]`, `scopes: [system/Patient.rs]` and the public JWK Set
as inline `jwkSet`. No launch or redirect URIs. Do not reuse the EHR client's registration.

`npm run start:reference` runs the supplied example. Each search requests a fresh token;
the token has no patient context, lasts at most five minutes and has no refresh token.
Patient search requires an identifier; `GET /Patient` is not a list of the whole cohort.

There are no participant tests to complete. Keep your EHR app alongside this optional
flow as described in [PROGRESSION.md](./PROGRESSION.md).
