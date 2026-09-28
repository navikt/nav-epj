# SMART hackathon track contract

This document defines the contract between the published SMART on FHIR track and `nav-epj`. Every
track requirement maps to responsible endpoints, cohort requirements, starter tasks, acceptance
criteria, current gaps, and owning implementation todos. The track is ready when every gap below is
closed or explicitly descoped by the track lead.

Owning todos refer to the delivery plan reproduced below for reference.

## Sources

- Published track: <https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/smart-track.html>
- Published event guide: <https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/index.html>
- SMART App Launch IG (STU 2.2): <https://hl7.org/fhir/smart-app-launch/>
- Local Implementation Guide source (sibling checkout, not part of this repository):
  `../Norwegian-FHIR-Hackathon-2026/Norwegian-FHIR-hackathon-2026/input/pagecontent/smart-track.md`

## Approved boundaries

Approved scope boundaries for this delivery:

- Participant starters live in a separate, public GitHub template repository, not in `nav-epj`.
- Starters use Node 22, TypeScript, and Fastify with server-rendered pages.
- The supported participant runtime is local Node 22 only. No Codespaces.
- Three standalone EHR-launch variants: public (`none`), confidential symmetric
  (`client_secret_basic`), confidential asymmetric (`private_key_jwt`).
- Reference implementations are visible in the starter repository, not hidden.
- The event cohort is eight curated SyntPop patients: four with fødselsnummer, four with d-nummer.
- The maximum team roster is 10 teams. Teams have a minimum of one person with no upper size
  limit. Credentials are provisioned per team, not per participant, and no participant cap is
  inferred from the 10-team limit. Concurrency acceptance targets the registered participant count
  plus 25% headroom, while credential generation is capped at 10 team slots.
- Demographics are imported from Persontjenesten before the event; clinical data is generated from
  deterministic scenario recipes in `nav-epj`.
- No Persontjenesten response snapshot is committed and no offline demographic fallback exists. A
  lost database requires a Persontjenesten re-import to rebuild the cohort.
- Team clients are pre-provisioned from deployment configuration and secrets. There is no dynamic
  client registration UI or API.
- Every currently published Bronze, Silver, and Gold exercise is in scope, including Observation
  write-back and SMART Backend Services.

## Implementation todo reference

| # | Todo |
|--:|---|
| 1 | Freeze the executable track contract (this document) |
| 2 | Prove Persontjenesten connectivity and contract |
| 3 | Extend the EPJ clinical model (identity type, birth date, gender, measurements) |
| 4 | Build the idempotent curated-cohort provisioner |
| 5 | Complete and correct the FHIR R4 surface |
| 6 | Show imported and written data in the EPJ |
| 7 | Make SMART registration and discovery event-ready |
| 8 | Implement SMART Backend Services |
| 9 | Build team credential tooling and mentor runbook |
| 10 | Create the starter and reference repository |
| 11 | Add black-box event acceptance and rehearsal |
| 12 | Finish participant and operator documentation |

## Minimum curated cohort

Referenced by every exercise row below as "cohort requirement". Owned by todo 4.

| Requirement | Minimum |
|---|---:|
| Patients | 8 |
| Fødselsnummer | 4 |
| D-nummer | 4 |
| Organisations | 2 |
| Clinicians with HPR number | 2 |
| Active encounters | 1 per patient |
| Historical encounters | At least 1 for 6 patients |
| ICPC-2 diagnoses | Present across at least 4 patients |
| ICD-10 diagnoses | Present across at least 2 patients |
| LOINC measurement types | At least 3 |
| Trend-ready patients (3+ readings of one measurement) | At least 3 |

## What the track provides

| Track promise | Responsible endpoint/capability | Cohort requirement | Starter variant | Acceptance ID | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| EPJ with its own SMART authorization server and FHIR R4 API | `GET /fhir/launch`, `GET /oidc/authorize`, `POST /oidc/token`, `GET /fhir/.well-known/smart-configuration`, `GET /fhir/metadata` | n/a | n/a | `PROVIDE-EPJ-01` | EHR launch, PKCE, and code exchange work. Runtime client registration accepts public clients (`none`), but discovery omits `none` from `token_endpoint_auth_methods_supported`. Discovery also advertises registration, refresh, introspection, and revocation capabilities that do not exist | 5, 7 |
| Synthetic Norwegian patient data (FNR/DNR, HPR clinicians, orgnummer organisations, consultations, ICPC-2/ICD-10 diagnoses, measurements) | `GET /fhir/Patient/{id}`, `GET /fhir/Encounter`, `GET /fhir/Condition`, `GET /fhir/Observation` (new), `GET /fhir/Practitioner/{id}`, `GET /fhir/PractitionerRole?practitioner=`, `GET /fhir/Organization/{id}` | Full minimum cohort above | n/a | `PROVIDE-COHORT-01` | `pasient` has no identity type, birth date, or gender; there is no measurement table; Persontjenesten integration is commented-out scaffolding | 2, 3, 4, 5 |
| Starter skeletons, one per client authentication type, with numbered launch `TODO`s | n/a (participant-side) | n/a | `public`, `client-secret`, `private-key-jwt`, `backend-services` | `PROVIDE-STARTER-01` | Starter repository does not exist yet | 10 |
| Credentials on the day (10 team slots, provisioned per team): `client_id`, secret or key pair, a test clinician, and a patient list | Client registry (`smart.clients` config), HelseID test-clinician login | Patient list export from the cohort | n/a | `PROVIDE-CREDS-01` | Client registrations are hand-written per-environment YAML; there is no per-team generator | 7, 9 |
| Mentors circulating throughout the day | n/a (operational) | n/a | n/a | `PROVIDE-MENTOR-01` | Mentor runbook covers provisioning, credential issuance, and single-team rotation | 9 |

## Bronze

*It launched.*

| Item | Responsible endpoint/capability | Cohort requirement | Starter variant / TODO | Acceptance ID | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| Launch, server-side code exchange, display name/age and active consultation | `GET /fhir/launch` → `GET /oidc/authorize` → `POST /oidc/token` → `GET /fhir/Patient/{id}` → `GET /fhir/Encounter?patient=` → `GET /fhir/Practitioner/{id}`, `GET /fhir/PractitionerRole?practitioner=`, `GET /fhir/Organization/{id}` | Any of the 8 patients, all with a birth date and an active encounter | All three EHR-launch variants; launch-sequence TODOs 1-10 | `BRONZE-LAUNCH-01` | Launch and code exchange work end to end. `Patient` has no `birthDate`, so age cannot be computed | 3, 5, 6, 10, 11 |

## Silver

*It is useful, and it is honest.* Pick one.

| Option | Responsible endpoint/capability | Cohort requirement | Starter variant / TODO | Acceptance ID | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| Clinical mini-app (trend/summary/flag) | `GET /fhir/Condition?subject=&encounter=`, `GET /fhir/Observation?subject=&code=` (new) | At least 3 trend-ready patients; ICPC-2/ICD-10 diagnoses present | Extends TODO 10's fetch/render step; reference-only, not separately numbered | `SILVER-CLINICAL-01` | `Condition` includes an encounter reference only when filtered by encounter, because domain `Diagnose` lacks `konsultasjonId`. `Observation` does not exist | 3, 4, 5, 11 |
| Scope detective (request narrower scopes, decode tokens, prove enforcement) | `POST /oidc/token`, any scoped `GET /fhir/*` route | Any patient | Extends TODO 5 (build authorization request with scopes) | `SILVER-SCOPE-01` | Patient/user/system scope enforcement exists; needs a contract test proving rejection for the hackathon's exact scope set | 5, 11 |
| Confidential client (`client_secret_basic`) | `POST /oidc/token` with HTTP Basic credentials | Any patient | `client-secret` variant; variant-specific token-exchange TODO | `SILVER-CONFIDENTIAL-01` | Implemented. Needs coverage in the per-team registry and black-box suite | 7, 10, 11 |

## Gold

*It writes back, or it does something nobody expected.* Pick one.

| Option | Responsible endpoint/capability | Cohort requirement | Starter variant / TODO | Acceptance ID | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| Write-back (create a document or measurement) | `POST /fhir/Observation` (new), `POST /fhir/DocumentReference` | Any patient with an active encounter | Any EHR-launch variant; reference-only, not a numbered launch TODO | `GOLD-WRITEBACK-01` | `Observation` persistence does not exist. `POST /fhir/DocumentReference` returns `200` instead of `201`, has no `Location` header, and requires the client to supply the resource ID | 5, 6, 11 |
| Asymmetric client authentication (`private_key_jwt` end to end) | `POST /oidc/token` with a signed client assertion; client's inline JWK Set | Any patient | `private-key-jwt` variant; variant-specific token-exchange TODO | `GOLD-ASYMMETRIC-01` | Implemented for clients with a remote `jwksUri`. A participant app on localhost cannot publish a reachable `jwksUri`; inline public JWK Set registration is missing | 7, 10, 11 |
| Backend services (system-to-system, no user) | `POST /oidc/token` with `grant_type=client_credentials`; system-scoped `GET /fhir/*` | Cohort must be searchable without a launch context | `backend-services` variant; reuses the asymmetric assertion helper | `GOLD-BACKEND-01` | `client_credentials` is not implemented. Access-token/principal types already allow a patient-less token, but grant handling and token policy do not exist | 8, 10, 11 |
| Break it (tamper `state`, replay `code`, wrong `aud`, expired token, out-of-scope resource) | Same `authorize`/`token`/`fhir` endpoints, exercised with adversarial input | Any patient | All variants; security-assertion tests, not a numbered launch TODO | `GOLD-BREAKIT-01` | PKCE, `state`, and replay protection exist for the authorization-code flow; each attack needs an explicit, checked automated test | 10, 11 |

## Acceptance contract

Todo 11 binds these stable acceptance IDs to executable test paths and commands.

| ID | Focus | Machine-checkable assertion |
|---|---|---|
| `PROVIDE-EPJ-01` | EPJ SMART discovery and capabilities | `GET /fhir/.well-known/smart-configuration` returns `200 OK` with `token_endpoint_auth_methods_supported` containing `none`, `client_secret_basic`, and `private_key_jwt`, matching registered client methods. Discovery and `GET /fhir/metadata` advertise only implemented routes, grants, and capabilities. |
| `PROVIDE-COHORT-01` | Curated SyntPop cohort | Provisioner idempotently populates exactly 8 patients (4 FNR, 4 DNR). `GET /fhir/Patient/{id}`, `GET /fhir/Encounter?patient={id}`, `GET /fhir/Condition?subject={id}`, `GET /fhir/Observation?subject={id}`, `GET /fhir/Practitioner/{id}`, `GET /fhir/PractitionerRole?practitioner={id}`, and `GET /fhir/Organization/{id}` return `200 OK`. `Patient` has `birthDate` and Norwegian identifier system; diagnoses use ICPC-2/ICD-10; observations use LOINC/UCUM; at least 3 patients have 3+ readings of one measurement. |
| `PROVIDE-STARTER-01` | Starter and reference repositories | Starter variants (`public`, `client-secret`, `private-key-jwt`, `backend-services`) run on Node 22. Unimplemented launch TODOs fail with informative test failures; reference implementations pass 100%. |
| `PROVIDE-CREDS-01` | Team credentials | Generator creates credential packets for up to 10 team slots (provisioned per team, not per participant) with zero key collisions or cross-team credential leakage. Packets contain client IDs, secrets/keys, test clinician credentials, and cohort roster. |
| `PROVIDE-MENTOR-01` | Mentor runbook | Runbook documents verified operational commands for cohort provisioning, credential generation, single-team rotation, and launch verification. |
| `BRONZE-LAUNCH-01` | Bronze launch and display | Full launch flow (`GET /fhir/launch` → `GET /oidc/authorize` → `POST /oidc/token` → `GET /fhir/Patient/{id}` → `GET /fhir/Encounter?patient=` → `GET /fhir/Practitioner/{id}`, `GET /fhir/PractitionerRole?practitioner=`, `GET /fhir/Organization/{id}`) succeeds for all 8 cohort patients across all 3 EHR-launch variants. App displays patient name, calculated age, active encounter, clinician, and organisation. |
| `SILVER-CLINICAL-01` | Clinical mini-app | `GET /fhir/Condition?subject={id}&encounter={id}` and `GET /fhir/Condition?subject={id}` return `200 OK` searchset Bundles. Condition resources include encounter references when encounter-filtered, and domain `Diagnose` carries `konsultasjonId`. `GET /fhir/Observation?subject={id}&code={loinc}` returns `200 OK` Bundle with ordered numeric values and UCUM units; app displays trends across 3+ readings. |
| `SILVER-SCOPE-01` | Scope enforcement | Token endpoint grants requested subset of scopes. In-scope `GET /fhir/*` returns `200 OK`. Out-of-scope request returns `403 Forbidden` with an `OperationOutcome`. |
| `SILVER-CONFIDENTIAL-01` | Symmetric client auth | `POST /oidc/token` with HTTP Basic credentials returns `200 OK` and access token. Invalid secret returns `401 Unauthorized` with `error=invalid_client` without consuming authorization code. |
| `GOLD-WRITEBACK-01` | Resource write-back | `POST /fhir/Observation` and `POST /fhir/DocumentReference` return `201 Created` with a `Location: /fhir/{resource}/{id}` header and server-assigned ID. Created resources persist and appear on EPJ consultation refresh. Cross-patient write returns `403 Forbidden`. |
| `GOLD-ASYMMETRIC-01` | Asymmetric client auth | `POST /oidc/token` with `private_key_jwt` assertion signed by client key registered via inline JWK Set returns `200 OK` and access token. Assertion with invalid signature, expired timestamp, replayed `jti`, or wrong audience returns `401 Unauthorized` with `error=invalid_client`. |
| `GOLD-BACKEND-01` | Backend services | `POST /oidc/token` with `grant_type=client_credentials` and client assertion returns `200 OK` with patient-less token, `expires_in <= 300`, and no refresh token. System token permits authorized system-scoped FHIR queries and rejects user/launch-scoped operations with `401` or `403`. |
| `GOLD-BREAKIT-01` | Negative security tests | Adversarial attacks fail with expected HTTP status and OAuth/FHIR error code: tampered/missing state returns `302` with `error=invalid_request` or `400 Bad Request`; replayed authorization code returns `400 Bad Request` with `error=invalid_grant`; wrong audience or unknown key returns `401 Unauthorized` with `error=invalid_client`; expired token returns `401 Unauthorized`; out-of-scope resource access returns `403 Forbidden` with `OperationOutcome`. |

## Related documentation

- [SMART client authentication](./smart-client-authentication-guide.md): how each
  `token_endpoint_auth_method` is verified today.
- [Notes](./notes.md): local and environment OAuth configuration.
