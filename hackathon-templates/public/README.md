# Public EHR-launch starter

Build a SMART app and launch it from nav-epj. This variant uses `none` at the token endpoint, so
it needs no secret or key. PKCE still applies.

1. Use Node 22 and run `npm ci`.
2. Configure it as described in [GUIDE.md](./GUIDE.md).
3. Build your app from `src/steps.ts`, and run `npm start`.

The host registers `tokenEndpointAuthMethod: none` and `grantTypes: [authorization_code]` with the
launch and redirect URLs from the guide. For the supplied key pair, use `private-key-jwt/`.
Feature ideas are in [EXERCISES.md](./EXERCISES.md).
