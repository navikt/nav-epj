# Client-secret EHR-launch starter

Build a SMART app that authenticates its token request with `client_secret_basic`. PKCE still
applies and the secret stays on the Node server.

1. Use Node 22 and run `npm ci`.
2. Configure it as described in [GUIDE.md](./GUIDE.md), including `CLIENT_SECRET`.
3. Build your app from `src/steps.ts`, and run `npm start`.

The host registers `tokenEndpointAuthMethod: client_secret_basic` and
`grantTypes: [authorization_code]` with the matching secret and the launch and redirect URLs from the
guide. `client_secret_post` is not supported. For the supplied key pair, use `private-key-jwt/`.
Feature ideas are in [EXERCISES.md](./EXERCISES.md).
