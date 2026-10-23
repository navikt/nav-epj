# Build your app and launch it in nav-epj

Use this starter as scaffolding for your own SMART on FHIR app. The host supplies team
credentials, including public/private keys for key-pair clients, and access to nav-epj on
the day. The goal is a live launch with synthetic patient context, not passing a test suite.

## Get started

Install Node 22 (`.nvmrc` selects it if you use nvm), open the chosen starter, and run:

```sh
npm ci
```

Read `src/app.ts` for the routes and `src/steps.ts` for the numbered SMART launch sequence.
Fill in or adapt the skeleton as you build your app. `src/reference.ts` contains the supplied
implementation when you need help. Optional clinical/write-back examples are in
`src/extensions.ts`; you can use them, adapt them or write your own FHIR calls.

No tests are assigned to participants. The tests in this repository maintain our reference
code; they do not determine whether your app is complete.

## Configuration supplied by the host

Set the following in your terminal or IDE's run configuration. Do not commit credentials.

| Variable | Applies to | Value |
|---|---|---|
| `FHIR_BASE_URL` | All | Exact FHIR base supplied by the host, without a trailing slash |
| `AUTH_ISSUER` | All | Trusted authorization issuer supplied by the host |
| `CLIENT_ID` | All | Your assigned ID for this client type |
| `SCOPES` | All | Space-separated scopes agreed with the host |
| `PORT` | All | Defaults to `3100` |
| `REDIRECT_URI` | EHR launch | Defaults to `http://localhost:3100/callback`; must exactly match registration |
| `CLIENT_SECRET` | Client-secret only | Shared secret for a symmetric client registration |
| `PRIVATE_JWK_PATH` | Key-pair and backend | Path to the host-supplied private JWK file |
| `PATIENT_IDENTIFIER` | Backend only | Supplied synthetic identifier as `system|value` |

### Using the public/private keys supplied on the day

Choose `private-key-jwt/` for an EHR-launched app with key-pair authentication.
The reference accepts the **RSA 3072 / RS384** private JWK that nav-epj generates, and also
an **EC P-384 / ES384** private JWK. Either way the JWK needs a matching `alg` (`RS384` or
`ES384`), `use: sig` and a `kid` matching the public key registered by the host. The
assertion is signed with that algorithm.

Keep the private key on your laptop, for example in the ignored `keys/` directory,
and set `PRIVATE_JWK_PATH` to its path. The host registers the corresponding **public**
JWK Set in nav-epj. Never send the private key to the EPJ, expose it in the browser, or
paste it into Git or screenshots. There is no key-generation task in this starter.

For a localhost app, the public JWK Set is registered inline as `jwkSet`; a deployed
nav-epj instance cannot fetch keys from a JWKS endpoint on your laptop.

The keys authenticate your client at the token endpoint. Your app obtains an access token
through the SMART code exchange; the private key is not itself a FHIR access token.
The public starter needs no key, and the client-secret alternative needs a matching
shared-secret registration instead.

### Scopes and local addresses

The supplied EHR reference reads only the patient and active consultation by default.
Its requested and registered Bronze scopes are:

```text
openid fhirUser launch patient/Patient.r patient/Encounter.s
```

`fhirUser` permits reading the authenticated practitioner's own resource in nav-epj.
Ask the host to register additional scopes for your chosen features. Do not request offline
access: neither this starter nor the current nav-epj implements refresh-token exchange.

For the Backend Services example, use `system/Patient.rs` with a separate backend
registration and the roster's `PATIENT_IDENTIFIER`.

For a **local** nav-epj instance, the usual non-secret values look like this. Use the host's
actual event addresses and client ID on the day:

```sh
export FHIR_BASE_URL='http://localhost:8080/fhir'
export AUTH_ISSUER='http://localhost:8080/oidc'
export CLIENT_ID='your-assigned-client-id'
export SCOPES='openid fhirUser launch patient/Patient.r patient/Encounter.s'
```

On PowerShell, use `$env:CLIENT_ID = 'your-assigned-client-id'`. Use your IDE or a
non-echoing shell prompt for secrets rather than putting them in shell history.

### Optional clinician, roles and organization

These details are not needed for Bronze. ID-token verification and `fhirUser` remain
part of the launch flow; verifying the clinician's identity does not require fetching
their Practitioner resource.

To add the care-team view, agree `patient/PractitionerRole.s patient/Organization.r`
with the host and add them to `SCOPES`. In your `src/steps.ts` implementation, you can
reuse the optional reference behavior: replace the existing type-only reference import
with `import * as reference from "./reference.ts";`, then implement the method as:

```typescript
async loadChart(config, tokens, context) {
  return reference.loadChart(config, tokens, context, { includeCareTeam: true });
}
```

The normal renderer also displays the extra fields. Alternatively, implement those reads
yourself. The reference adds Practitioner, PractitionerRole and the encounter's
serviceProvider Organization as a group. Adding scopes alone does not enable these reads;
`start:reference` also uses the basic chart by default. Restart and launch afresh after
changing scopes.

Once explicitly enabled, the extra reads must succeed: missing permissions or an absent
serviceProvider are reported as errors, not silently hidden. With the feature disabled,
no care-team requests are made and a missing serviceProvider does not block the chart.

## Launch from nav-epj

Start your app:

```sh
npm start
```

For the three EHR variants, confirm the host's registration matches:

| Setting | Default |
|---|---|
| Launch URI | `http://localhost:3100/launch` |
| Redirect URI | `http://localhost:3100/callback` |
| Launch mode | `tab` |
| Grant | `authorization_code` |
| Authentication | The method described in your starter's README |

Log into nav-epj as the supplied test clinician, select a supplied patient and active
consultation, and open your app from the app picker. nav-epj supplies `iss` and a one-time
`launch` value. Opening the starter's home page alone does not create launch context.
Do not invent a launch ID or reuse an old callback URL.

The starter opens in a separate tab to avoid third-party-cookie issues with an HTTPS EPJ
and an HTTP localhost app. Keep the hostname consistent: `localhost` and `127.0.0.1`
have different cookie jars. If you change ports, update both the redirect URI and registration.
The server binds to loopback only.

To compare with our complete example, stop your process and run `npm run start:reference`.
It still uses your host-supplied registration and key/secret.

Backend Services is the exception: it has no EHR launch or clinician login. Open its
local home page and follow the search link. Do not register a backend-only client in the
EPJ app picker.

## Keep building

Show the launched patient's details in your app, then choose an idea from
[EXERCISES.md](./EXERCISES.md). Keep a working launch while adding features or changing
authentication; [PROGRESSION.md](./PROGRESSION.md) explains how.

This is a local learning scaffold: sessions are in memory, restarting Node requires a new
launch, and there is no database, deployment setup or token refresh. Tokens stay on the
server; cookies carry random session identifiers. Keep state, PKCE and ID-token verification
when adapting the code: they are part of the SMART flow being demonstrated.

## Troubleshooting

| Symptom | Check |
|---|---|
| `TODO n` / HTTP 501 | The named launch function is still a stub; implement it or consult the reference |
| Startup configuration error | Supply the variables required by your client type |
| Unknown client or rejected redirect | Check the exact client ID, method and localhost URI with the host |
| Missing state / no pending launch | Start a fresh launch in the same browser |
| Missing ID token or patient context | Requested and registered scopes must include `openid fhirUser launch` |
| Token endpoint HTTP 401 | Check the key ID, key format and assertion audience, or the shared secret |
| FHIR HTTP 403 | Compare requested scopes with the granted `scope` in the token response |
| Session expired | Launch again; there is no refresh flow |
| Empty data | Use the supplied cohort and supported search parameters |

Use mentor-side logs for upstream error details. Do not put raw tokens into online decoders.
