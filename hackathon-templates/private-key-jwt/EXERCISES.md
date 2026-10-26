# Ideas for your SMART app

The [published track](https://hl7norway.github.io/Norwegian-FHIR-Hackathon-2026/currentbuild/smart-track.html)
offers Bronze, Silver and Gold ideas. They describe what you can build, not tests to pass. The
Silver and Gold examples are in `src/extensions.ts`, and you can borrow, adapt or write your own FHIR
calls. Setup is in [GUIDE.md](./GUIDE.md). Bronze, Silver and most Gold ideas assume an EHR launch
(`public/`, `client-secret/`, `private-key-jwt/`). `backend-services/` covers only the Backend
Services idea under Gold.

## Bronze: it launched

EHR variants. Show the patient's name and age with the ongoing consultation. The skeleton in
`src/steps.ts` follows this sequence:

1. Receive `iss` and `launch`, and check the trusted FHIR issuer.
2. Discover the authorization and token endpoints.
3. Generate the PKCE verifier and challenge.
4. Store state and the launch transaction on the server.
5. Redirect to authorization with your scopes.
6. Check state when the callback returns.
7. Exchange the code server-side with your registered client authentication.
8. Verify the ID token before reading its claims.
9. Read patient and encounter from the token response (next to `access_token`), and the clinician from the
   `fhirUser` claim of the verified ID token.
10. Fetch the FHIR resources and render the page.

Afterwards, walk a teammate through the redirects in the browser's network tab. Tokens, secrets and
private keys stay on the server.

## Which request needs which scope?

`r` is read by ID, `s` is search and `c` is create. In EHR variants, select the encounter matching the launch (not the
first search hit). Searches return Bundles, and no data is different from a failed request.

| Feature | FHIR request | Scopes beyond the Bronze set |
|---|---|---|
| Name, age, consultation | `GET Patient/{patient}`, `GET Encounter?patient={patient}` | None |
| Clinician, roles, organisation | Practitioner, `PractitionerRole?practitioner=`, Organization from `serviceProvider` | `patient/PractitionerRole.s patient/Organization.r` |
| Diagnoses | `GET Condition?subject={patient}&encounter={encounter}` | `patient/Condition.s` |
| Measurements | `GET Observation?subject={patient}&code=http://loinc.org\|8310-5` | `patient/Observation.s` |
| Write a measurement | `POST Observation` | `patient/Observation.c` (add `.r` to read back) |
| Write a note | `POST DocumentReference` | `patient/DocumentReference.c` (add `.r` to read back) |
| Backend search | `GET Patient?identifier={system\|value}` | `system/Patient.rs` |

## Silver: useful and honest

EHR variants. Pick one.

**Clinical mini-app.** Show a summary, trend or flag from diagnoses and measurements. Diagnoses
carry codes from two systems: ICPC-2 (primary care, for example `K86` hypertension) and ICD-10
(`I10`). Measurements use LOINC to say what was measured (`8310-5` is body temperature) and UCUM for
the unit (`Cel`). Show the code system and unit with each value, never mix units in one trend, and
do not imply a trend from too few readings. `clinicalSummary` in `src/extensions.ts` reads the
encounter's conditions and observations for a LOINC code.

**Scope detective.** Launch with fewer scopes and compare what you requested with `scope` in the token
response. Show one allowed read and one refused one, and inspect the refused HTTP status and
`OperationOutcome`. For example, enable the care-team view, remove `patient/PractitionerRole.s` and
launch again. Decoding a token is inspection, not verification.

**Confidential client.** Move a working public app to `client_secret_basic` and explain what changed
at the token endpoint. A wrong secret returns `401 invalid_client`. PKCE, state and the redirects stay.

## Gold: write-back or a different flow

Pick one. Write-back and Break it use an EHR launch, Backend Services does not.

**Write-back.** Create a measurement or a note from an explicit user action, never on page load. The
examples in `src/extensions.ts` (`createResource`, `temperature`, `journalNote`) are not called
automatically. Add a POST handler with input validation, a session-bound CSRF token and an Origin check,
and take patient and encounter from the launch session. Expect `201` with an ID and `Location`, then
read it back. Measurements appear in the Målinger tab, and the note in the journal editor after you
return to the workspace. The document is a narrow DocumentReference whose `description` becomes the
note: `attachment.data` and `attachment.url` are rejected, so it does not upload a PDF. Check native
browser return yourself and tell a mentor if it differs.

**Asymmetric authentication.** Use `private-key-jwt/` with the supplied key pair. Sign the client
assertion as described in [GUIDE.md](./GUIDE.md#client-authentication). See `clientAssertion` in
`src/reference.ts`.

**Backend Services.** Use `backend-services/` (`discover`, `backendToken`, `backendSearch`) for a separate system-to-system app with its own
registration. It gets a token with no patient, no refresh token and a lifetime of at most five minutes,
and searches Patient by the supplied identifier. System access does not remove the query requirements.

**Break it.** Provoke one rejection at a time and name who should reject it. Compare the exact status
and error with what you expected, and do not capture credentials or raw tokens.

| Rejecter | Provoke | Expect |
|---|---|---|
| Your app | Callback with wrong `state`, or a replayed callback | Your app refuses before any token request |
| Your app | ID token with the wrong audience (must be your client ID) | `verifyIdentity` throws |
| Authorization server | Reused code: repeat the token exchange itself | `invalid_grant` |
| Authorization server | Wrong `code_verifier` | `invalid_grant` |
| Authorization server | Wrong, expired or replayed client assertion (`private-key-jwt/`) | `401 invalid_client` |
| FHIR server | Resource outside your granted scopes | `403` with an `OperationOutcome` |
| FHIR server | Access token with the wrong audience (must be the FHIR base) | Rejected |

Audiences differ per token: the authorize request's `aud` is the FHIR base URL, the ID token's `aud`
is your client ID, and the assertion's `aud` is the token endpoint. A replayed callback is stopped by
your app's consumed transaction, so it proves nothing about the server. To test code reuse, send the
same code to the token endpoint a second time.

## Keep a working launch

EHR variants. Save a working launch in your team repository before trying a new idea. To switch client authentication,
copy your `src/steps.ts` and helpers into a fresh copy of the target starter, keep its `src/variant.ts`,
package files and `src/config.ts`, and change only the token request (TODO 7). The supplied
`tokenRequest` picks the method from the variant. Use the target registration's own credentials, and run a
fresh launch. Keep `backend-services/` as a separate app: it shares assertions with the key-pair
variant, not the browser session.
