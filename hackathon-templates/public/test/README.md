# Reference implementation tests

These are maintainer tests for the supplied reference code, HTTP/session scaffolding,
FHIR helpers and local fixtures. They are **not participant assignments or acceptance
criteria for a team's app**.

From the starter directory:

```sh
npm test
npm run typecheck
```

The tests call `src/reference.ts` and `src/extensions.ts` directly and run the app in
reference mode. They do not execute the unfinished functions in `src/steps.ts`, and
there is no participant/reference selector or challenge-level runner.

Tests use temporary synthetic data and keys, not the public/private keys issued to
participants on the day. They make no requests to the event EPJ. A successful run
checks our example code; it does not prove a team's app launches in nav-epj or replace
the host's live rehearsal.
