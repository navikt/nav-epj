# SMART hackathon task guidance

**Draft for developer review.** Proposed participant guidance, not the final event handout.
This document does not change the starters or the approved
[track contract](../docs/smart-hackathon-contract.md). Review decisions are collected at the end.

The levels follow the [published SMART track](https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/smart-track.html):
Bronze gets an app launched, Silver adds a useful feature or explores authorization, and
Gold explores write-back or a different flow. Pick one option at Silver and one at Gold;
there is no requirement to build every option.

The goal is to build an app, launch it in nav-epj and explain what happens.
There are **no participant test suites to satisfy**. Observation and discussion prompts
below are learning aids, not automated grading criteria. The repository tests maintain
our reference implementations.

## Before you start

The host supplies EPJ access, a synthetic patient roster, client registration details
and the public/private keys for key-pair clients on the day. Do not generate replacement
keys or use real patient data.

Choose the starter matching your assigned registration, not your preferred medal:

| Starter | Use it for |
|---|---|
| [public](./public/README.md) | EHR launch without client credentials |
| [client-secret](./client-secret/README.md) | EHR launch with a shared secret |
| [private-key-jwt](./private-key-jwt/README.md) | EHR launch with your supplied key pair |
| [backend-services](./backend-services/README.md) | A separate system-to-system app, without EHR launch |

You can do Bronze with a key-pair client. You do not have to move through public,
shared-secret and asymmetric clients in that order. If you already use asymmetric
authentication, choose another extension or explore how your assertion works at Gold.

Copy one complete starter folder, including dotfiles, into your team's workspace.
Use Node 22 and run these commands **inside that folder**:

```sh
npm ci
npm start
```

Before starting the app, follow that starter's `GUIDE.md` to set the host-supplied
configuration in your terminal or IDE. The [shared guide](./public/GUIDE.md) explains
the variables and troubleshooting. Never commit credentials or private keys.
This draft assumes local Node 22, not Codespaces.

Confirm with the host that your EHR registration uses your exact launch URI,
redirect URI and authentication method. Defaults are `http://localhost:3100/launch`
and `http://localhost:3100/callback`, opening in a separate tab (`launchMode: tab`).
Use `localhost` consistently rather than alternating with `127.0.0.1`.
Run one starter at a time unless you have separately registered ports.

## Bronze: it launched

**Build:** an app opened from nav-epj that shows the selected patient's name and age,
plus the active consultation. Be able to explain the browser redirects and the
server-side calls. Aim for this first working launch before adding clinical features.

Start by reading `src/app.ts`: it already supplies routes, in-memory transactions and
sessions, cookies and HTML rendering. The ten functions in `src/steps.ts` are your
launch outline. Their names and numbering match the steps below in all three EHR starters.
You can adapt the scaffold; reproducing the reference function layout is not the goal.

`src/reference.ts` is available throughout. To see a complete example, stop your app
and run `npm run start:reference`, then launch it from nav-epj with your assigned
registration. Stop reference mode and return to `npm start` to run your own implementation.
Both modes need live EPJ access; reference mode is not an offline demo.

**Optional extra:** clinician, roles and organization are not required for Bronze.
The default `loadChart` reads only the patient and encounter. You can explicitly enable
the care-team view later; see the [guide](./public/GUIDE.md#optional-clinician-roles-and-organization).
ID-token verification remains required even when you do not display clinician details.

### 1. Receive the EHR launch: `readLaunch`

**Input:** nav-epj opens `/launch` with query parameters `iss` and `launch`.

Validate that `iss` exactly matches your configured `FHIR_BASE_URL`, and that `launch`
is present and nonempty. Return the validated values to the launch route.
Treat `launch` as an opaque, one-time value; do not invent or interpret it.
Do not fetch URLs from arbitrary incoming `iss` values.

**Observe and explain:** open your app through the EPJ app picker with a selected
patient and active consultation. Locate `/launch` in the browser's network tab.
Explain why visiting your app's home page alone does not establish patient context.
Here, `iss` identifies the FHIR server; it is not the authorization-server issuer.

### 2. Discover trusted endpoints: `discover`

**Input:** the configured FHIR base, authorization issuer and client type.

Fetch `FHIR_BASE_URL + "/.well-known/smart-configuration"`. Read the authorization
endpoint, token endpoint and `jwks_uri`. Check the advertised issuer against
`AUTH_ISSUER` and the endpoint origins against the trusted authorization origin,
as the reference does. Check support for your client authentication method,
the `authorization_code` grant and PKCE `S256`.

Return validated metadata for the transaction; do not merely accept any returned URL.
The supplied HTTP helpers reject redirects.

**Observe and explain:** identify which endpoint the browser will visit and which
endpoints your server will call. Why do the FHIR base and authorization issuer have
separate configuration values?

### 3. Create the PKCE proof: `pkce`

**Input:** a fresh launch, not a reused verifier from a previous session.

Generate a cryptographically random verifier. Hash it with SHA-256 and encode the
result as unpadded base64url to obtain the challenge. Return both values.
Node's `randomBytes` and `createHash` are useful starting points.

**Observe and explain:** follow where the two values go in steps 4, 5 and 7.
Only the challenge goes through the browser; keep the verifier server-side.
Explain how the verifier binds the later code exchange to this launch.
PKCE remains necessary when the client also has a secret or a private key.

### 4. Create a launch transaction: `newTransaction`

**Input:** the validated launch, discovery metadata and PKCE proof.

Generate a fresh random OAuth `state` value and record the creation time.
Return a transaction containing these values and the inputs.
The supplied `/launch` route stores it on the server and sets an opaque transaction
cookie. Do not put the verifier, tokens or credentials in browser storage.

**Observe and explain:** distinguish the cookie's transaction identifier from OAuth
`state`. Explain how the callback is associated with the initiating browser.
The scaffold expires pending launches after five minutes and loses them on restart.

### 5. Redirect to authorization: `authorizationUrl`

**Input:** the configuration and stored transaction.

Build the discovered authorization URL using `URL` and `URLSearchParams`, with:

| Parameter | Value |
|---|---|
| `response_type` | `code` |
| `client_id` | Your registered client ID |
| `redirect_uri` | The exact registered callback URI |
| `scope` | The space-separated scopes agreed with the host |
| `aud` | The validated launch `iss`, identifying the FHIR base |
| `launch` | The opaque EHR launch value |
| `state` | Your transaction's state |
| `code_challenge` | Your PKCE challenge |
| `code_challenge_method` | `S256` |

Return the URL; the scaffold performs the browser redirect.
For the current chart reference, request and register:

```text
openid fhirUser launch patient/Patient.r patient/Encounter.s
```

**Observe and explain:** follow the redirect to authorization. Find parameter names
without sharing their sensitive values. Why is `aud` the FHIR base here, while a
client assertion in step 7 uses the token endpoint as its audience?

### 6. Accept only the matching callback: `validateCallback`

**Input:** the callback query and pending transaction retrieved by the scaffold.

Check the returned `state` against the stored value, check transaction age, and handle
an authorization error or missing code explicitly. Return the code only after these
checks. Use the reference's safe comparison pattern rather than skipping validation.
The callback route consumes the pending transaction so it cannot be used again.

**Observe and explain:** locate `/callback` in the browser's redirect chain.
Explain why a code alone is insufficient and why an expired or mismatched callback
must require a fresh launch rather than continuing with a default patient.

### 7. Exchange the code on the server: `exchange`

**Input:** the accepted code and the original transaction.

POST a form-encoded request to the discovered token endpoint with
`grant_type=authorization_code`, the code, the original `redirect_uri`, the PKCE
`code_verifier` and your client ID. Authenticate according to your registration:

| Client type | Token endpoint authentication |
|---|---|
| Public | No secret or assertion |
| Shared secret | HTTP Basic (`client_secret_basic`), not `client_secret_post` |
| Key pair | A signed assertion and the JWT-bearer `client_assertion_type` |

For key-pair clients, use the supplied private key with the `clientAssertion` reference
helper as a hint; Gold explains its claims. The current helper expects an ES384 / EC
P-384 private JWK. Follow `GUIDE.md` rather than assuming any key format works.

Validate the token response and keep it server-side. Note the granted `scope`,
`expires_in` and launch-context fields without printing raw tokens.

**Observe and explain:** the token POST is a server-to-server call, so it should not
appear as a browser request to the token endpoint. Explain why the authorization
code is not itself a FHIR access token. Do not add a refresh flow; relaunch on expiry.

### 8. Verify the clinician identity: `verifyIdentity`

**Input:** discovery metadata and the token response's ID token.

Use `jose` and the trusted authorization server's JWKS to verify the ID token before
reading its identity claims. Check signature, allowed algorithm, issuer, client
audience, expiry and required claims. Follow the reference's authorized-party checks.
Extract and validate the `fhirUser` Practitioner reference.

nav-epj currently signs ID tokens with RS256. This is distinct from the ES384 key
your client uses to sign its own assertions; do not use your client key to verify
the authorization server's ID token.

**Observe and explain:** distinguish the ID token, access token and client assertion.
Explain why decoding a JWT payload is not identity verification. Never paste tokens
into online decoders.

### 9. Assemble the launch context: `launchContext`

**Input:** the token response and the verified practitioner identifier.

Require the token response's `patient` and `encounter` identifiers and combine them
with the practitioner. Return the context for the server-side session.
Do not choose a patient from a free-text browser field or silently select the first
patient in a search result.

**Observe and explain:** distinguish the selected patient from the signed-in clinician.
Explain why patient and encounter come from the authorized launch context, while
the practitioner comes from the verified identity.

### 10. Read FHIR resources and show the app: `loadChart`

**Input:** the access token and stored launch context.

Use server-side FHIR calls with bearer authorization. The existing reference performs:

| Request relative to the FHIR base | Purpose |
|---|---|
| `Patient/{patient}` | Name and birth date for calculating age |
| `Encounter?patient={patient}` | Find the encounter identified by the launch |

Select the encounter matching the launch ID, check that its subject is the launched
patient and that its status is `in-progress`. Do not just take the first encounter.
Return the chart fields expected by the scaffold; its renderer escapes their values.
`src/http.ts` supplies FHIR, reference, name and age helpers.

Optional care-team details add `Practitioner/{practitioner}`,
`PractitionerRole?practitioner={practitioner}` and the encounter's serviceProvider
`Organization/{id}`. They are disabled by default, even if extra scopes are granted.
The reference enables them with `{ includeCareTeam: true }`; request/register
`patient/PractitionerRole.s patient/Organization.r` first. Failures in explicitly
enabled reads are surfaced, not ignored.

**Observe and explain:** compare your app with the selected patient and consultation
in nav-epj. Launch again with another supplied patient and explain how the context
changes without hard-coded IDs. Trace the full journey:

```text
EPJ app picker -> /launch -> authorization -> /callback -> /app
                             server-side token exchange and FHIR reads
```

If you get stuck, use the guide's troubleshooting table or ask a mentor. A `TODO n`
response means that function is still unfinished; a FHIR `403` calls for a scope
check, not bypassing authorization.

## Silver: useful and honest

Keep your working Bronze flow and pick one brief. These are feature choices, not
another prescribed sequence of functions. Optional helpers live in `src/extensions.ts`;
the scaffold does not automatically render their results.

### Option A: a clinical mini-app

**Build:** a small consultation summary or measurement history. For example, show the
active consultation's diagnoses alongside dated body-temperature readings.

Agree `patient/Condition.s patient/Observation.s` with the host in addition to the
launch scopes. Use `clinicalSummary` as a starting point:

```text
Condition?subject={patient}&encounter={encounter}
Observation?subject={patient}&code=http://loinc.org|8310-5
```

Construct query parameters with `URLSearchParams`. The first query is consultation-specific;
the second covers that patient's temperature history, not only the active encounter.
Make this distinction visible in your app.

Start with a dated list before drawing a chart. Sort readings by time, show units,
and distinguish no recorded data from a failed request. Use an explicit
"Not enough readings for a trend" message when appropriate; the supplied helper uses
three readings as its demonstration threshold, not as a clinical validity rule.
Do not combine incompatible units without deliberate conversion.

**Show and explain:** demonstrate a useful view for a supplied patient and explain
what it cannot conclude. If you add a flag, label it as a hackathon demonstration,
not a validated clinical recommendation.

### Option B: scope detective

**Explore:** the difference between requested, granted and required permissions.

Begin with a working launch and explicitly enable the optional care-team view,
including its registered scopes, as described in the guide. Confirm it works, then
remove `patient/PractitionerRole.s` from `SCOPES`, restart the app with that configuration
and launch afresh. The optional PractitionerRole search should now be refused with `403`.
The basic Bronze chart does not make that request. Because optional chart loading
fails, the normal requested/granted scope display may not render: inspect only
the scope strings locally on the server, without logging whole token responses.

Compare the authorization request's scopes with the token response's `scope` and
the permission required by the refused call. nav-epj can omit requested scopes
that are not registered, so requesting a scope does not prove it was granted.
Restore the scope, relaunch and compare behavior.

Use PractitionerRole for this demonstration rather than Organization: nav-epj's
patient-context Organization reads have different enforcement behavior.

**Show and explain:** describe which call was denied and why. Explain why changing
the rendered UI or adding a scope string cannot grant permissions the host has not
registered. Do not disable validation to make the screen load.

### Option C: a shared-secret confidential client

**Build:** the same launched app using a host-supplied `client_secret_basic`
registration. This option needs a separate matching registration; simply changing
your client ID's authentication method in code is not enough.

Use [PROGRESSION.md](./public/PROGRESSION.md) to keep your working launch functions
and feature code. Concentrate changes on configuration and token authentication.
The secret belongs only on the server. nav-epj does not support `client_secret_post`;
follow the reference's Basic encoding and the guide's credential requirements.

**Show and explain:** launch the same feature again. Identify what changed in the
token request and what did not: state, PKCE, callback validation and ID-token
verification are still required. If you already have a key-pair client, there is
no need to switch to a secret just to complete Silver.

## Gold: write-back or a different flow

Pick one extension. Decide what to build and use the constraints below to keep
the result understandable. More code is not the goal.

### Option A: write a measurement or document

**Build:** an explicit user action that creates one resource for the launched patient
and consultation, then reports the actual outcome. Choose either a measurement or
a document; both are not required.

Agree `patient/Observation.c` or `patient/DocumentReference.c` with the host,
plus the corresponding read/search permissions needed to retrieve the result.
Use `temperature`, `journalNote` and `createResource` in `src/extensions.ts` as
current implementation examples, not an already-wired feature.

Add a server-side POST handler and a submit action. Validate the input, use a
session-bound CSRF token and an Origin check, and take patient/encounter IDs from
the server-side launch session. Do not write on page load. Avoid accidental repeated
submissions, and do not blindly retry a create request whose outcome is unknown.

For a measurement, the supplied example uses temperature LOINC `8310-5`, UCUM `Cel`,
an effective timestamp and patient/encounter references. For a document, decide what
the text means and how the reader should recognize it before choosing metadata.

**Document reference for discussion:**
[syk-inn's DocumentReference example](https://github.com/navikt/syk-inn/blob/main/docs/fhir/document-reference.md)
illustrates a richer Norwegian document use case, including document type,
description, attachment, author and patient/encounter links. It is **not a drop-in
POST body for current nav-epj** or the finalized hackathon payload contract.
Its sick-note-specific legal context is not a general requirement for every app.

The current `journalNote` helper uses document type `J01-2` from code system
`urn:oid:2.16.578.1.12.4.1.1.9602`, patient/encounter references and a `description`.
nav-epj stores that description as the note. Its narrow accepted attachment contains
`contentType: application/pdf` metadata, **not an uploaded PDF**. Attachment `data`,
`url` and richer fields from the linked example are currently rejected.
Do not present this helper as PDF generation or upload.

**Show and explain:** inspect the `201 Created` response, server-assigned ID and
`Location`, then read the resource back with suitable permissions. Look for a created
measurement in nav-epj's measurements view. Document display remains outstanding;
do not promise a journal-screen result until the host confirms support.
Show failures honestly rather than displaying success before the server responds.

**Pending host-provided links before publication:**

| Guidance to supply | Status |
|---|---|
| Event-approved write-back payloads/profiles and examples | Pending |
| Supported document content and metadata for the event | Pending |
| Where and how to view created resources in nav-epj, especially documents | Pending |

Until those links arrive, the current helpers explain local compatibility only.
Review this task against the final write-back contract before handing it to participants.

### Option B: understand and implement asymmetric client authentication

**Build or explore:** an EHR-launched app authenticated by your host-supplied key pair.
If this is already your Bronze client type, focus on explaining and deliberately
exercising the assertion instead of rebuilding the same flow.

Keep PKCE and the authorization-code flow. Use `private-key-jwt/` and its
`clientAssertion` helper as a reference. The host registers the public JWK Set
inline; a deployed EPJ cannot retrieve JWKS from your laptop's localhost.
Keep the private key on your machine and out of browser code, Git and screenshots.

An assertion identifies the client with `iss = sub = client_id`, uses the exact
discovered token endpoint as `aud`, includes a fresh `jti`, and has a short expiry.
The reference uses a two-minute lifetime and an ES384 key with the registered `kid`.
A fresh assertion is needed for each token request.

**Show and explain:** identify who signs the client assertion and who verifies it.
Contrast this with the authorization server's ID token and its signing keys.
Explain why an assertion alone cannot read FHIR data or replace user authorization.

### Option C: Backend Services without a clinician

**Build:** a separate app that searches for a supplied synthetic patient using
system-to-system authorization. Use `backend-services/` rather than removing
patient checks from your EHR app.

Obtain a dedicated backend registration, the supplied key pair and a roster identifier
from the host. The starter's three TODOs cover discovery, `backendToken` and
`backendSearch`. Request `grant_type=client_credentials` with a signed client assertion
and registered system scopes; the starter example uses `system/Patient.rs`.

Search `Patient?identifier={system|value}`, using the host-supplied
`PATIENT_IDENTIFIER` and proper query encoding. This is not an unfiltered patient
export. Do not assume system access enables arbitrary searches or every resource.

**Show and explain:** run the app without the EPJ app picker or clinician login.
Explain the absence of `/launch`, callback, PKCE, ID token and selected patient context.
The backend token is patient-less, lasts at most five minutes, and has no refresh token.
When needed, obtain a new token rather than fabricating launch context.
Explain why system scopes need a separate, deliberately authorized registration.

### Option D: break your own flow and explain the refusal

**Explore:** one failure at a time in your own app and the host-authorized test EPJ.
Coordinate with a mentor; do not probe other teams or unrelated systems.
Use only synthetic data and never share raw credentials, codes, assertions or tokens.

Choose a small experiment and predict which component should reject it:

| Experiment | Expected rejecting component |
|---|---|
| Change callback `state` | Your app, before exchanging the code |
| Reuse a consumed authorization code | Token endpoint |
| Give a client assertion the FHIR base as `aud` | Token endpoint; it expects its own URL |
| Use an expired access token | FHIR server, or the app's session check before a request |
| Request a resource interaction outside granted scopes | FHIR server |

Distinguish app-side refusal from upstream refusal. For example, replaying a callback
URL can fail at the consumed transaction before reaching token-endpoint code replay
protection. Do not claim to have observed the latter from the former.

**Show and explain:** record a sanitized status/error description, explain the boundary
that refused the request, and restore a normal launch afterward. A failed experiment
is not permission to remove a safeguard. Ask a mentor if the observed result differs
from your prediction.

## Developer review before publication

This is a proposed handout structure, not an implementation backlog or a change
to the approved contract. Resolve these points with the other developer and host:

| Topic | Review decision |
|---|---|
| Level of guidance | Is Bronze practical for a newcomer before lunch? Are Silver and Gold open enough? |
| Bronze scope | The default is now patient and active consultation only. Review the opt-in care-team instructions; identity verification is still required. |
| Authentication and levels | Make clear that supplied key-pair clients can start at Bronze; authentication variants are not mandatory medal stages. |
| Supplied keys | Confirm ES384 / EC P-384 private JWKs and matching public registration, or adapt the helpers and guides before publication. |
| Development environment | Reconcile the published Codespaces promise with the approved local-Node-only contract. This draft follows the latter. |
| Write-back | Add the host's final links and payload rules; confirm document display before promising it. Keep the syk-inn link as a use-case example. |
| Event rehearsal | Confirm live registration, addresses, scopes and cohort data with each supported starter. Maintainer checks are separate from participant tasks. |
| Distribution | Review this root draft before copying guidance into standalone starters or publishing the separate template repository. |
