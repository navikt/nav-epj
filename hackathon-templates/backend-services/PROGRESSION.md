# Keep your working launch as you build

Start with an app that launches from nav-epj. Keep that working while adding a feature
or changing client authentication. Bronze/Silver/Gold are ideas, not test gates.

## First working app

Choose the starter matching your team's registration. For the public/private keys issued
on the day, use `private-key-jwt/`. Implement the launch flow, run `npm start`, and open
the registered app from nav-epj with a synthetic patient and active consultation.

Once your app receives the context and displays the patient's details, save a copy or
checkpoint in your own team repository before trying a new direction.

## Add a feature in the same app

For a clinical summary, scope experiment, measurement or document, keep the launch code
and build your feature on top of the server-side token and context you already have.
`src/extensions.ts` provides examples, but your app can use its own structure and FHIR calls.
Ask the host to register any additional scopes, then make a fresh launch requesting them.

## Switch client authentication without starting over

Only the authentication part of the token request needs to change between the three
EHR variants. Discovery, PKCE, state, ID-token verification and FHIR context still apply.

1. Open a copy of the target starter and run `npm ci`.
2. Bring over the launch code you want to keep. In an unmodified scaffold, that means
   `src/steps.ts` and any helpers you wrote. Keep the target's `src/variant.ts`, package
   files and supplied configuration code.
3. Adapt `exchange` (TODO 7) to the target's method. The supplied `tokenRequest` helper
   already selects the method from the variant; if you use it, read and explain what changes.
   If you wrote your own exchange, add Basic authentication or the signed client assertion.
   Keep PKCE.
4. Configure the client ID and secret/key for the **target registration**. Do not reuse
   tokens, copy credentials into code or assume the previous client ID has the new method.
5. Run the new app and launch it from nav-epj. Check that the patient and consultation still
   appear and explain the different token request to a teammate.

For asymmetric authentication, use the private key supplied by the host and the matching
registered public key. There is no key-generation or test-completion task.

## Backend Services is a separate app

Keep your EHR-launched app and start `backend-services/` alongside it if that option interests
you. Reuse your understanding of discovery and assertions, not the browser launch session.
It needs its own backend registration and system scopes, with no clinician or callback.

## Use the reference when useful

Read `src/reference.ts` and `src/extensions.ts`, or run `npm run start:reference` with the
host's configuration to see the complete example. You can borrow and adapt code; the goal
is to understand the SMART flow and get your own app working in nav-epj.
