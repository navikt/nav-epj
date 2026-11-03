# nav-epj

## Overview

This application simulates an EHR system that launches applications using SMART on FHIR and exposes
healthcare data through a FHIR API.

## Local Development

### Prerequisites

Before running the application, make sure you have the following installed:

- [Node.js](https://nodejs.org/en/) (LTS)
- [Yarn](https://yarnpkg.com/) (`corepack enable`)
- [Docker](https://www.docker.com/)
- [Java 25](https://openjdk.org/projects/jdk/25/)


### Running the application locally

In addition to Node.js, Yarn and Docker, running the app locally requires PostgreSQL and Valkey.
Start these with:

```bash                                                                                                                                                                                                                                                                             ┃
docker-compose up -d
```

The frontend and backend must run in separate terminal windows. Backend code changes need a restart of `./gradlew runLocal`.

### Frontend

Navigate to the frontend directory and start the development server:

```bash
cd frontend
yarn
yarn dev
```

See [frontend/README.md](./frontend/README.md) for the architecture and design rules.

### Backend

From the backend directory, start the application using Gradle:

```bash
./gradlew runLocal
```

### Test data

Organisations, mock patients and fastlegelister are seeded by a repeatable Flyway migration. See
[docs/testdata.md](./docs/testdata.md) for how to add an organisation, a patient, a doctor's list or
a column.

### Breaking cutover to the English code names

The code, database schema and config now use English names (`ehr` instead of `epj`, `clinic`,
`practitioner`, `patient`, `consultation`, `clinical_note`, `measurement`). The UI text is still
Norwegian. The old and new versions are not compatible, so this is not a rolling deploy. Run through
these steps before the first deploy of the new version to an environment with data (dev):

1. Stop all old app instances first. Do not let old and new instances run side by side.
2. Reset the database schema. The new Flyway migrations `V1` to `V7` replace the old `V1` to `V11`
   and recreate the final schema, and the repeatable testdata migrations reseed it when
   `database.seedTestdata` is `true`. Data written to the old schema is lost by design.
3. Clear or let expire the short-lived launch and auth code contexts in Valkey (5 minutes and 60
   seconds). Their JSON field `orgnummer` is now `orgNumber`, so an old context fails to parse in the
   new version.
4. The session organisation cache moved from the key prefix `epj:session-org:` to `ehr:session-org:`.
   Clinicians are asked to pick an organisation again after login. The old keys expire on their own.
5. The root config section is `ehr:` (was `epj:`) and the base URL is `ehr.baseUrl`. `.nais/nais-dev.yaml`
   does not override it.
6. If the SMART client registry comes from the secret `nav-epj-client-config` as
   `smart.clientRegistryJson` (the secret is not in this repository, check it yourself), rename the
   keys in each element: `navn` to `name`, `beskrivelse` to `description` and `ikon` to `icon`. Owned
   icon ids changed too: `sykmelding` to `sick-note`, `vindu` to `window`, `mappe` to `folder`,
   `timebok` to `appointment-book`. The other icon ids (`app`, `journal`, `validator`) are unchanged,
   as are the OAuth keys such as `clientId`, `redirectUris` and `scopes`. The old keys are not
   aliased. They are ignored without an error, so an app without migrated keys stays registered but
   shows its client id as name, no description and the default `window` icon. An old icon id also
   falls back to `window`. Migrate them to keep
   the app display config.

### Session information

`GET /api/session` backs the Systeminformasjon page. It returns a fixed selection of claims from the
HelseID id_token that Wonderwall forwards (`iss`, `aud`, `name`, `hpr_number`) and the token's
issue and expiry times. It never returns raw tokens or the `pid` claim. With local development
security it returns `{ "idp": "local-stub", "claims": { "sub": "local-dev" } }`.

### Changing how a SMART app opens (window vs. new tab)

Each registered SMART client has a fixed `launchMode` set in its config entry under `smart.clients`
in `application.yaml` / `application-local.yaml`:

| `launchMode` | Behaviour |
| --- | --- |
| `iframe` (default) | Opens inside nav-epj, in the app tab ("Vindu"). |
| `tab` | Opens in its own browser tab ("Ny fane"); never framed, so it's excluded from the CSP `frame-src` list below. |
| `ask` | The clinician is asked each launch ("Velg visning") and can tick "Husk valget for denne appen" to remember the choice per app (stored in the browser's `localStorage`, scoped per clinician). |

There is no in-app setting to change this per clinician for `iframe`/`tab` clients — it's a
registry-level decision, matching the app's SMART client registry rather than a user preference.

### Security headers

Every response carries these headers (`SecurityHeaders.kt`):

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `frame-src 'self' <app origins>; frame-ancestors 'self'` |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=(), bluetooth=(), serial=(), display-capture=()` |

`<app origins>` is built at startup from the launch and redirect URIs of the registered SMART clients
(`smart.clients` or `smart.clientRegistryJson`), so a new app can be embedded by registering it.
Clients with `launchMode: tab` are left out because they are never framed. `frame-ancestors` is
`'self'` and not `'none'` because `/fhir/launch` and `/oidc/authorize` run inside nav-epj's own
iframe.

The policy only sets framing directives. It has no `default-src`, so scripts, styles and
connections are not restricted.

### Testing the SMART launch flow with SMART on FHIR Validator

To test the SMART launch flow locally,
[the SMART on FHIR Validator](https://github.com/navikt/smart-on-fhir-validator) must also be
running. Follow the instructions in the validator repository to start it before testing the launch
flow.

### HOW-TO authenticate SMART clients based on auth method

Read [this guide](./docs/smart-client-authentication-guide.md) for instructions and code examples on
how to make your SMART on FHIR application work with nav-epj.

### SMART on FHIR Hackathon track

Read [the track contract matrix](./docs/smart-hackathon-contract.md) for the mapping between the
published hackathon track and this repository's endpoints, cohort data, and acceptance tests.
The hackathon roster has 32 patients in four organisations, and launch data is scoped to the session
organisation ([docs/hackathon-roster.md](./docs/hackathon-roster.md)).

Participant starters live in [navikt/smart-on-fhir-templates](https://github.com/navikt/smart-on-fhir-templates):
standalone local apps for public, client-secret, private-key-jwt and Backend Services flows,
with Bronze/Silver/Gold exercises and visible reference solutions.

### Finding new available dependencies
``` bash
./gradlew dependencyUpdates
```

### Upgrading the Gradle wrapper version to latest
``` bash
./gradlew :wrapper --gradle-version latest
```

### Contact

This project is maintained by [navikt/helseopplysninger](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/nav-epj/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-symfoni](https://nav-it.slack.com/archives/C07MY3KCDS5)