# Ideas for your SMART app

The [published track](https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/smart-track.html)
offers Bronze, Silver and Gold ideas. They describe what you can build, **not tests to pass**.
Use the host's synthetic patients and credentials, including the public/private keys supplied
on the day. Choose the client type matching your registration.

## Bronze: it launched

Build an app that launches from nav-epj and shows the patient's name and age together
with the active consultation. Clinician, roles and organization are an optional addition,
described in [GUIDE.md](./GUIDE.md#optional-clinician-roles-and-organization).
Any of the three EHR client types can do this, including the key-pair client.

The skeleton lays out this sequence in `src/steps.ts`:

1. Receive `iss` and `launch` from nav-epj and check the trusted FHIR issuer.
2. Discover the authorization and token endpoints.
3. Generate the PKCE verifier and challenge.
4. Store state and the launch transaction on the server.
5. Redirect to authorization with the requested scopes.
6. Check state when the callback returns.
7. Exchange the code server-side using your registered client authentication.
8. Verify the ID token before reading its identity claims.
9. Read patient, encounter and clinician context.
10. Fetch the FHIR resources and render your app.

Adapt the scaffold to your app rather than aiming for a prescribed implementation.
`src/reference.ts` is available whenever you need help. After a successful launch,
walk someone through the redirects in the browser's network tab. Keep access tokens,
secrets and private keys on the server.

## Silver: useful and honest

Pick something useful to add to your working app.

**Clinical mini-app.** Read diagnoses and measurements and show a summary, trend or flag.
Request/register `patient/Condition.s patient/Observation.s` in addition to the launch
scopes. `clinicalSummary` in `src/extensions.ts` is an example: it reads conditions for
the active encounter and observations for a selected LOINC code. You can instead show
the patient's broader history. Label the context, units and missing data honestly; do
not imply a trend when there are too few readings or mix incompatible units.

**Scope detective.** Request fewer scopes and observe what nav-epj grants and refuses.
For example, first enable the optional care-team reads and their scopes, then remove
`patient/PractitionerRole.s`, launch again and inspect the denied PractitionerRole search.
The basic chart no longer makes that search. Compare the requested scopes with `scope` in the token response.
Inspect tokens only locally and remember that decoding is not verification.

**Confidential client.** If the host supplies a shared-secret registration, adapt a
working public app to use `client_secret_basic`. Explain what changed at the token
endpoint. PKCE, state and the browser redirects are still necessary; nav-epj does not
support `client_secret_post`.

## Gold: write-back or a different flow

Pick an extension that interests your team.

**Write-back.** Create a measurement or a document from an explicit action in your app.
`src/extensions.ts` includes server-side examples you can adapt:

```typescript
import { createResource, temperature, journalNote } from "./extensions.ts";

// Example alternatives; invoke only the action the user requested.
const measurement = await createResource(config, tokens, temperature(context, 37.2));
const note = await createResource(config, tokens, journalNote(context, "Synthetic hackathon note"));
```

These examples are not called automatically. Add your own POST handler and submit action
with input validation, a session-bound CSRF token and an Origin check. Use the patient
and encounter already stored in the launch session, not an arbitrary browser-supplied ID.
Never write on page load.

Ask the host to register `patient/Observation.c` or `patient/DocumentReference.c`, plus
read/search permissions if needed. Observe the `201` response, server-assigned ID and
`Location`, then read the resource back and look for the measurement in nav-epj.

nav-epj's document example stores `description` as a journal note. It accepts a narrow
DocumentReference shape with `application/pdf` metadata, **not an uploaded PDF**:
`attachment.data` and `attachment.url` are rejected. The reference helper matches that
shape. Document display in the EPJ remains outstanding in the track contract.

**Asymmetric authentication.** Use the host-supplied key pair with the `private-key-jwt`
starter. Sign a client assertion with `iss = sub = client_id`, the discovered token
endpoint as `aud`, a short expiry and a fresh `jti`. The host registers the public JWK Set;
the private key stays with your app. See `clientAssertion` in `src/reference.ts`.

**Backend Services.** Build a separate system-to-system app using the `backend-services`
starter. It uses a dedicated registration, a signed assertion and pre-authorized system
scopes. There is no clinician, EHR launch or selected patient. The example searches
Patient by a host-supplied synthetic identifier.

**Break it.** Explore rejection behavior in your own app and the host-authorized test EPJ.
Try an incorrect state, a reused authorization code, a wrong assertion audience, an expired
token or a resource outside your scopes. Discuss the responses with a mentor. Do not
capture credentials or raw tokens in screenshots, and do not attack unrelated systems.

## Keep the working launch

See [PROGRESSION.md](./PROGRESSION.md) for moving between client types without rebuilding
the whole launch flow. Demonstrate your app in nav-epj and explain what it does; there is
no participant test suite or required function layout to satisfy.
