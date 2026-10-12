# SMART on FHIR hackathon starters

Starting points for participants to **build their own app and launch it in nav-epj**.
These are local hackathon apps, not production services or test-driven assignments.
The host supplies the team registration details, public/private keys and EPJ access on the day.

| Folder | Client authentication | When to choose it |
|---|---|---|
| [public/](./public/) | `none` | Explore EHR launch without a client secret or key |
| [client-secret/](./client-secret/) | `client_secret_basic` | Use a client registered with a shared secret |
| [private-key-jwt/](./private-key-jwt/) | `private_key_jwt` | Launch with the public/private key pair supplied by the host |
| [backend-services/](./backend-services/) | `private_key_jwt`, `client_credentials` | Optional system-to-system flow, without EHR launch or a clinician |

Each folder is a standalone Node 22 / TypeScript / Fastify project. Copy one whole folder,
including its dotfiles, into your team repository. There are no parent/sibling dependencies.
Choose the client type matching your registration; the key-pair EHR starter is
`private-key-jwt/`, not the backend-only app.

## Participant workflow

1. Install Node 22 and run `npm ci` inside your chosen starter.
2. Follow its `GUIDE.md` to configure the host-supplied client ID, addresses and secret/key.
3. Build your app. `src/steps.ts` outlines the launch sequence; `src/reference.ts` is
   visible for help, not a prescribed implementation you must reproduce.
4. Run `npm start`, log into nav-epj, select a synthetic patient and launch your registered app.

`npm run start:reference` runs the supplied example if you need to see the complete flow.
Both modes require a matching registration for live launch. All variants default to
port 3100; run one at a time unless the host registers different ports.

Bronze/Silver/Gold are [ideas for what to build](./public/EXERCISES.md), not test targets.
[PROGRESSION.md](./public/PROGRESSION.md) explains how to keep a working launch when adding
features or changing client authentication. The aim is a working app in nav-epj.

## Maintainers

The separate [task guidance draft](./TASK-GUIDANCE.md) proposes a guided Bronze walkthrough
and Silver/Gold briefs for developer review. It includes pending write-back guidance
and publication decisions; it is not yet the final participant handout.
The accompanying [SMART/FHIR map draft](./SMART-FHIR-MAP.md) summarizes browser/server
calls, token types, resources, scopes and how to choose a suitable synthetic patient.

The tests protect **our reference implementation, helpers and fixtures**. They do not
grade participant code, and participants do not need to complete a test suite.
From a chosen starter, maintainers can run:

```sh
npm test
npm run typecheck
```

These checks use temporary synthetic data and keys, not the credentials issued on the day.
They do not replace a live rehearsal against the host's nav-epj instance.

Common scaffolding, docs and tests are maintained in `public/`. From this directory:

```sh
node scripts/sync-common.mjs
```

This maintainer-only command overwrites common files in the other variants. It preserves
variant selection, individual READMEs, variant-specific client-authentication tests and
the backend-specific app, steps and tests. Participants should not run it on their own work.

This folder remains a local staging area for the separate public template repository
required by [the track contract](../docs/smart-hackathon-contract.md). It does not provision
credentials, change nav-epj's runtime, deploy an app or publish a repository.
