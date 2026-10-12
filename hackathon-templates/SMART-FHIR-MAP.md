# Your SMART / FHIR map

**Draft for review:** a quick reference beside the [guided tasks](./TASK-GUIDANCE.md),
not a test sheet. Use only the host's synthetic patients and supplied credentials.

## Who calls whom?

```text
BROWSER: carries redirects, not access tokens

nav-epj app picker
  -> your app /launch?iss=...&launch=...
  -> nav-epj authorization endpoint
  -> your app /callback?code=...&state=...
  -> your app /app (rendered patient view)

YOUR APP SERVER: holds credentials, PKCE verifier, tokens and launch context

/launch   -> FHIR /.well-known/smart-configuration (discover trusted endpoints)
/callback -> authorization server /token          (exchange code + PKCE)
          -> authorization server JWKS             (verify ID token)
/app      -> FHIR /Patient/{patient}                (name and age)
          -> FHIR /Encounter?patient={patient}      (find launched consultation)
```

`iss` is the FHIR base; `AUTH_ISSUER` is the authorization-server issuer.
The browser's network tab shows redirects. Token exchange and FHIR calls happen on
your server. Never share raw callback URLs, tokens, secrets or private keys.

## Which token is which?

| Item | Purpose | Who creates it / who uses it? |
|---|---|---|
| Access token | Permission to call FHIR within granted scopes | Authorization server issues it; your server sends it to FHIR |
| ID token | Verified identity of the signed-in user | Authorization server signs it; your app verifies it before reading `fhirUser` |
| Client assertion | Authenticate a key-pair client at the token endpoint | Your server signs it with the supplied private key; authorization server verifies it |

The authorization **code** is a one-use input to token exchange, not a FHIR token.
`state` binds the callback to the launch transaction. PKCE binds the code exchange
to the app's original verifier. Neither replaces client authentication.

**Three audiences:** authorization request `aud` = FHIR base;
ID token `aud` = your client ID; client assertion `aud` = exact token endpoint.
Decoding a JWT is not verification. The client assertion and ID token use different
signing keys; the current reference uses ES384 and RS256 respectively.

## What should I read or write?

Base launch scopes: `openid fhirUser launch patient/Patient.r patient/Encounter.s`.
`r` means read by ID, `s` search, and `c` create. Request scopes **and** have the host
register them; inspect the granted `scope` rather than assuming all were granted.

Paths below are relative to the supplied FHIR base. Build queries with `URLSearchParams`.

| Feature | FHIR request | Permissions beyond base launch | Choose a roster patient with... |
|---|---|---|---|
| Bronze: name, age, consultation | `GET Patient/{patient}`; `GET Encounter?patient={patient}` | None | A birth date and active consultation |
| Optional clinician and roles | `GET Practitioner/{practitioner}`; `GET PractitionerRole?practitioner={practitioner}` | `patient/PractitionerRole.s`; `fhirUser` already permits the own-practitioner read in nav-epj | An authenticated clinician with roles |
| Optional organization | `GET Organization/{id}` from encounter `serviceProvider` | Include `patient/Organization.r` for the reference view; nav-epj has a patient-context read exception | An encounter linked to an organization |
| Consultation diagnoses | `GET Condition?subject={patient}&encounter={encounter}` | `patient/Condition.s` | Diagnoses on that consultation |
| Temperature history | `GET Observation?subject={patient}&code=http://loinc.org|8310-5` | `patient/Observation.s` | Several dated readings in compatible units |
| Write a measurement | `POST Observation` | `patient/Observation.c`; add `.r` to read back by ID | An active consultation |
| Write a note | `POST DocumentReference` | `patient/DocumentReference.c`; add `.r` to read back by ID | An active consultation; final event instructions pending |

`{patient}` and `{encounter}` come from the token response; `{practitioner}` comes
from the verified `fhirUser`. Select the encounter matching the launch, not the first
search result. Clinical searches return Bundles, not single resources.
No data is different from a failed request; show dates, units and limitations honestly.

The default Bronze reference does **not** fetch clinician, roles or organization.
The [optional care-team view](./public/GUIDE.md#optional-clinician-roles-and-organization)
enables those reads together. For scope detective, enable it first, then remove
`patient/PractitionerRole.s` and relaunch to observe the refused role search.

**Write-back:** submit explicitly, take patient/encounter from the server session,
and confirm `201`, the assigned ID and `Location`. The current document helper stores
description text, not uploaded PDF data. Measurements are displayed in nav-epj;
document display and final payload/viewing links remain pending.
The [syk-inn document example](https://github.com/navikt/syk-inn/blob/main/docs/fhir/document-reference.md)
is a richer use-case reference, not a drop-in request for this starter.

## Different authentication, or a different flow?

Public, shared-secret and key-pair clients can all do Bronze. Use the registration
you receive; authentication types are not a mandatory Bronze-to-Gold sequence.

Backend Services is separate: no browser launch, clinician, PKCE or ID token.
Use a dedicated key-pair registration, `client_credentials` and `system/Patient.rs`
to search `Patient?identifier={system|value}` with the supplied synthetic identifier.
Its token is short-lived and patient-less; system access does not mean unrestricted access.

**Host preparation:** add roster labels for diagnosis-rich and trend-ready patients
before distributing this sheet. No real event patient IDs or available readings are
assumed here. This draft stays at template root until reviewed for publication.
