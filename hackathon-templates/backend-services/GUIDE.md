# Build your app and launch it in nav-epj

This starter is scaffolding for your own SMART on FHIR app. The goal is a live launch with a
synthetic patient, not passing a test suite. Choose the folder that matches your registration:
`public/`, `client-secret/`, `private-key-jwt/` (host-supplied key pair) or `backend-services/`.

## Get started

Install Node 22 (`.nvmrc` selects it with nvm), then run `npm ci` in your starter.
`src/steps.ts` holds the numbered launch sequence, `src/app.ts` the routes and
`src/reference.ts` a complete implementation. `src/extensions.ts` has optional Silver and Gold
examples.

| Command | Purpose |
|---|---|
| `npm start` | Run your app (unfinished steps answer HTTP 501 with `TODO n`) |
| `npm run start:reference` | Run the supplied example with the same configuration |
| `npm test`, `npm run typecheck` | Check the supplied reference code only, not your app |

## Configuration

Set these in your terminal or run configuration. Never commit credentials.

| Variable | Applies to | Value |
|---|---|---|
| `FHIR_BASE_URL` | All | FHIR base from the host, no trailing slash |
| `AUTH_ISSUER` | All | Authorization issuer from the host |
| `CLIENT_ID` | All | Your assigned ID for this client type |
| `SCOPES` | All | Space-separated scopes agreed with the host |
| `PORT` | All | Defaults to `3100` |
| `REDIRECT_URI` | EHR launch | Defaults to `http://localhost:3100/callback`, must match the registration |
| `CLIENT_SECRET` | `client-secret` | Shared secret |
| `PRIVATE_JWK_PATH` | `private-key-jwt`, `backend-services` | Path to your private JWK file |
| `PATIENT_IDENTIFIER` | `backend-services` | Synthetic identifier as `system\|value` |

For a local nav-epj (use the host's real values on the day), shared by every variant:

```sh
export FHIR_BASE_URL='http://localhost:8080/fhir'
export AUTH_ISSUER='http://localhost:8080/oidc'
export CLIENT_ID='your-assigned-client-id'
```

EHR launch (`public/`, `client-secret/`, `private-key-jwt/`):

```sh
export SCOPES='openid fhirUser launch patient/Patient.r patient/Encounter.s'
```

`backend-services/` (no launch; `system|value` is a roster identifier):

```sh
export SCOPES='system/Patient.rs'
export PRIVATE_JWK_PATH='keys/private.jwk.json'
export PATIENT_IDENTIFIER='system|value'
```

On PowerShell use `$env:CLIENT_ID = '...'`. Enter secrets through your IDE or a non-echoing
prompt, not shell history.

## Client authentication

All EHR variants keep authorization code and PKCE. Only the token request differs.

| Variant | Token endpoint authentication | You also need |
|---|---|---|
| `public/` | `none` | Nothing secret. PKCE is the only proof |
| `client-secret/` | `client_secret_basic` (`client_secret_post` is not supported) | `CLIENT_SECRET` |
| `private-key-jwt/` | `private_key_jwt`: a signed client assertion | `PRIVATE_JWK_PATH` |
| `backend-services/` | `private_key_jwt` with `client_credentials`, no launch | `PRIVATE_JWK_PATH`, `PATIENT_IDENTIFIER`, `SCOPES=system/Patient.rs` |

PKCE protects the code exchange. Client authentication identifies your app. Neither replaces the other.

**Keys.** The reference accepts the RSA 3072 / RS384 private JWK that nav-epj generates, or an
EC P-384 / ES384 one. The JWK needs a matching `alg`, `use: sig` and a `kid` equal to the
registered public key. Keep it outside Git (the ignored `keys/` directory works). The host registers
only the public JWK Set, inline as `jwkSet`, because a deployed nav-epj cannot fetch a JWKS from your
laptop. The assertion uses `iss = sub = client_id`, the discovered token endpoint as `aud`,
a short expiry and a fresh `jti`. The key is not a FHIR token: your app still gets an access token
from the code exchange.

**Scopes (EHR variants).** The default Bronze scopes are `openid fhirUser launch patient/Patient.r patient/Encounter.s`.
Requesting a scope does not grant it, so ask the host to register extra ones and compare with `scope`
in the token response. Do not request `offline_access`: there is no refresh flow.

## Launch from nav-epj

EHR variants only; `backend-services/` is described [below](#backend-services).

Run `npm start`, log in as the supplied test clinician, select a patient and open your app from the
app picker. The picker needs an ongoing consultation: select a patient who has one, or start one
with *Start konsultasjon*. Your registration should say:

| Setting | Value |
|---|---|
| Launch URI | `http://localhost:3100/launch` |
| Redirect URI | `http://localhost:3100/callback` |
| Launch mode | `tab` |
| Grant | `authorization_code` |

To repeat a launch by hand, start from the app picker each time. nav-epj supplies `iss` and a
one-time `launch` value, so opening your home page or reusing an old callback URL does not work.
Use the same host name throughout (`localhost` and `127.0.0.1` have separate cookies). If you
change the port, change the redirect URI and the registration too.

## Optional care-team view

EHR variants only. Clinician, roles and organisation are not part of Bronze. To add them, agree
`patient/PractitionerRole.s patient/Organization.r` with the host and add them to `SCOPES`. In
`src/steps.ts`, change `import type * as reference from "./reference.ts"` to
`import * as reference from "./reference.ts"` (a value import), and have `loadChart` return
`reference.loadChart(config, tokens, context, { includeCareTeam: true })`.
Once enabled, missing permissions or a missing `serviceProvider` are shown as errors.

## Backend Services

`backend-services/` has no launch, callback or app-picker entry, and needs its own
`client_credentials` registration. Implement the three steps in `src/steps.ts`: `discover`,
`backendToken` (signed assertion, patient-less token) and `backendSearch`
(`GET Patient?identifier=`). Run `npm start`, open `http://localhost:3100` and follow the search link.

## What is real and what is mocked

Launch, token exchange and FHIR reads in your running app go to the real nav-epj and need a matching
registration. `npm test` uses a local mock server with temporary keys and synthetic data. It checks our
reference code, not your app and not the event instance.

## Patients and organisations

The event data has 32 synthetic patients in four organisations. In an EHR launch you only see the
patients of your session organisation, and changing organisation after launch fails with
`Launch token was issued for another organisation.` Four patients (one per organisation) have an
ongoing consultation; for any other, start one first. Ask the host which patients fit your idea,
for example one with many dated readings. `backend-services/` searches by roster identifier instead.

## Troubleshooting

| Symptom | Check |
|---|---|
| `TODO n` / HTTP 501 | The step is a stub: implement it or read the reference |
| Startup configuration error | Set the variables for your variant |
| Unknown client or rejected redirect | Client ID, authentication method and URIs match the registration |
| Missing state or pending launch | Start a fresh launch in the same browser |
| Missing ID token or patient (EHR) | Scopes must include `openid fhirUser launch` |
| App picker disabled (EHR) | Start a consultation for the patient |
| Token endpoint 401 | Secret, `kid`, key format or assertion `aud` |
| FHIR 403 | Compare requested scopes with the granted `scope` (backend: `system/Patient.rs`) |
| Session expired (EHR) | Launch again; sessions are in memory |

Tokens stay on the server and cookies carry random session IDs. Do not paste raw tokens into
online decoders. Next: [EXERCISES.md](./EXERCISES.md).
