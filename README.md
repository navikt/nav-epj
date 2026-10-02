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

The frontend and backend must run in separate terminal windows.

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