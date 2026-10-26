# SMART on FHIR hackathon starters

Participants build their own app and launch it in nav-epj. The starters are local apps,
not production services or test assignments. The host supplies registrations and keys on the day.

| Folder | Client authentication |
|---|---|
| [public/](./public/) | `none` |
| [client-secret/](./client-secret/) | `client_secret_basic` |
| [private-key-jwt/](./private-key-jwt/) | `private_key_jwt` |
| [backend-services/](./backend-services/) | `private_key_jwt` with `client_credentials`, no EHR launch |

Each folder is a standalone Node 22 / TypeScript / Fastify project. Participants copy one whole folder,
dotfiles included, and follow its `GUIDE.md` and `EXERCISES.md`. The folders stage the separate public
template repository required by [the track contract](../docs/smart-hackathon-contract.md).

## Maintainers

Edit shared files only in `public/`. Then run from this directory:

```sh
node scripts/sync-common.mjs
```

This overwrites the common files in the other variants and keeps their READMEs, `variant.ts`,
client-authentication tests and the backend-specific app. CI fails if it leaves a diff.

From a starter, `npm test` and `npm run typecheck` check our reference code against a local mock
with temporary keys. They do not grade participant apps and do not replace a live rehearsal
against the host's nav-epj. The event data is the 32-patient roster in
[docs/hackathon-roster.md](../docs/hackathon-roster.md), scoped per session organisation.
