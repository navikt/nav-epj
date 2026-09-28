# SMART hackathon track contract

This document is the executable contract between the published SMART on FHIR track and what
`nav-epj` actually delivers. Every promise and exercise in the track description maps to a
responsible endpoint, a cohort requirement, a starter TODO, an acceptance test, its current gap,
and the implementation todo that owns closing that gap. Treat this file as the release gate: the
track is not ready until every "Gap" cell below is either closed or explicitly descoped by the
track lead.

Owning todos refer to the numbered implementation todos in the approved delivery plan, reproduced
below for reference since the plan itself is not checked into this repository.

## Sources

- Published track: <https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/smart-track.html>
- Published event guide: <https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/index.html>
- SMART App Launch IG (STU 2.2): <https://hl7.org/fhir/smart-app-launch/>
- Local Implementation Guide source (sibling checkout, not part of this repository):
  `../Norwegian-FHIR-Hackathon-2026/Norwegian-FHIR-hackathon-2026/input/pagecontent/smart-track.md`

## Approved boundaries

These decisions are final for this delivery. Later work must implement within them, not
reconsider them:

- Participant starters live in a separate, public GitHub template repository, not in `nav-epj`.
- Starters use Node 22, TypeScript, and Fastify with server-rendered pages.
- The supported participant runtime is local Node 22 only. No Codespaces.
- Three standalone EHR-launch variants: public (`none`), confidential symmetric
  (`client_secret_basic`), confidential asymmetric (`private_key_jwt`).
- Reference implementations are visible in the starter repository, not hidden.
- The event cohort is eight curated SyntPop patients: four with fødselsnummer, four with d-nummer.
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

| Contract | Minimum |
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

| Track promise | Responsible endpoint/capability | Cohort requirement | Starter variant | Acceptance test | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| EPJ with its own SMART authorization server and FHIR R4 API | `GET /fhir/launch`, `GET /oidc/authorize`, `POST /oidc/token`, `GET /fhir/.well-known/smart-configuration`, `GET /fhir/metadata` | n/a | n/a | Discovery and `CapabilityStatement` match implemented routes | EHR launch, PKCE, and code exchange work. Discovery advertises registration, refresh, introspection, and revocation capabilities that do not exist | 7, 12 |
| Synthetic Norwegian patient data (FNR/DNR, HPR clinicians, orgnummer organisations, consultations, ICPC-2/ICD-10 diagnoses, measurements) | `GET /fhir/Patient/{id}`, `GET /fhir/Encounter`, `GET /fhir/Condition`, `GET /fhir/Observation` (new) | Full minimum cohort above | n/a | Cohort provisioning command produces a validated 8-patient roster; sampled resources validate against FHIR R4 | `pasient` has no identity type, birth date, or gender; there is no measurement table; Persontjenesten integration is commented-out scaffolding | 2, 3, 4, 5 |
| Starter skeletons, one per client authentication type, with numbered launch `TODO`s | n/a (participant-side) | n/a | `public`, `client-secret`, `private-key-jwt`, `backend-services` | Each starter's `TODO` tests fail until implemented; each reference suite passes | Starter repository does not exist yet | 10 |
| Credentials on the day: `client_id`, secret or key pair, a test clinician, and a patient list | Client registry (`smart.clients` config), HelseID test-clinician login | Patient list export from the cohort | n/a | Team credential generator produces one packet per team with no cross-team leakage | Client registrations are hand-written per-environment YAML; there is no per-team generator | 7, 9 |
| Mentors circulating throughout the day | n/a (operational) | n/a | n/a | Mentor runbook covers provisioning, credential issuance, and single-team rotation | Runbook does not exist yet | 9 |

## Bronze

*It launched.*

| Item | Responsible endpoint/capability | Cohort requirement | Starter variant / TODO | Acceptance test | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| Launch, server-side code exchange, display name/age and active consultation | `GET /fhir/launch` → `GET /oidc/authorize` → `POST /oidc/token` → `GET /fhir/Patient/{id}` → `GET /fhir/Encounter?patient=` | Any of the 8 patients, all with a birth date and an active encounter | All three EHR-launch variants; launch-sequence TODOs 1-10 | All eight patients launch; the app shows correct name, computed age, active encounter, clinician, and organisation | Launch and code exchange work end to end. `Patient` has no `birthDate`, so age cannot be computed | 3, 5, 6, 10, 11 |

## Silver

*It is useful, and it is honest.* Pick one.

| Option | Responsible endpoint/capability | Cohort requirement | Starter variant / TODO | Acceptance test | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| Clinical mini-app (trend/summary/flag) | `GET /fhir/Condition?subject=&encounter=`, `GET /fhir/Observation?subject=&code=` (new) | At least 3 trend-ready patients; ICPC-2/ICD-10 diagnoses present | Extends TODO 10's fetch/render step; reference-only, not separately numbered | Diagnoses and a measurement trend render from FHIR data | `Condition` search works only when filtered by encounter, because the domain diagnosis object drops its consultation reference. `Observation` does not exist | 3, 4, 5, 11 |
| Scope detective (request narrower scopes, decode tokens, prove enforcement) | `POST /oidc/token`, any scoped `GET /fhir/*` route | Any patient | Extends TODO 5 (build authorization request with scopes) | Requested and granted scopes are visible; an out-of-scope resource request is rejected | Patient/user/system scope enforcement exists; needs a contract test proving rejection for the hackathon's exact scope set | 5, 11 |
| Confidential client (`client_secret_basic`) | `POST /oidc/token` with HTTP Basic credentials | Any patient | `client-secret` variant; variant-specific token-exchange TODO | Basic exchange succeeds; an incorrect secret fails | Implemented. Needs coverage in the per-team registry and black-box suite | 7, 10, 11 |

## Gold

*It writes back, or it does something nobody expected.* Pick one.

| Option | Responsible endpoint/capability | Cohort requirement | Starter variant / TODO | Acceptance test | Current status/gap | Owning todo |
|---|---|---|---|---|---|---|
| Write-back (create a document or measurement) | `POST /fhir/Observation` (new), `POST /fhir/DocumentReference` | Any patient with an active encounter | Any EHR-launch variant; reference-only, not a numbered launch TODO | Create returns `201 Created` with `Location` and a server-generated ID; the resource appears in the EPJ after refresh | `Observation` persistence does not exist. `POST /fhir/DocumentReference` returns `200` instead of `201`, has no `Location` header, and requires the client to supply the resource ID | 5, 6, 11 |
| Asymmetric client authentication (`private_key_jwt` end to end) | `POST /oidc/token` with a signed client assertion; client's inline JWK Set | Any patient | `private-key-jwt` variant; variant-specific token-exchange TODO | Valid assertion succeeds; replay, wrong audience, unknown key, and expiry all fail | Implemented for clients with a remote `jwksUri`. A participant app on localhost cannot publish a reachable `jwksUri`; inline public JWK Set registration is missing | 7, 10, 11 |
| Backend services (system-to-system, no user) | `POST /oidc/token` with `grant_type=client_credentials`; system-scoped `GET /fhir/*` | Cohort must be searchable without a launch context | `backend-services` variant; reuses the asymmetric assertion helper | Client credentials returns a five-minute, patient-less system token that can only access authorized data | `client_credentials` is not implemented. Access-token/principal types already allow a patient-less token, but grant handling and token policy do not exist | 8, 10, 11 |
| Break it (tamper `state`, replay `code`, wrong `aud`, expired token, out-of-scope resource) | Same `authorize`/`token`/`fhir` endpoints, exercised with adversarial input | Any patient | All variants; security-assertion tests, not a numbered launch TODO | Every listed attack fails for the intended, standards-shaped reason | PKCE, `state`, and replay protection exist for the authorization-code flow; each attack needs an explicit, checked automated test | 10, 11 |

## Related documentation

- [SMART client authentication](./smart-client-authentication-guide.md), how each
  `token_endpoint_auth_method` is verified today.
- [Notes](./notes.md), local and environment OAuth configuration.
