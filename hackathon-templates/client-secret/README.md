# Client-secret EHR-launch starter

Build a SMART app that launches from nav-epj and authenticates its token request with
`client_secret_basic`. Keep PKCE; the secret stays on the Node server.

1. Use Node 22 and run `npm ci`.
2. Follow [GUIDE.md](./GUIDE.md), including the client ID and shared secret from the host
   if your team is using this registration type.
3. Build your app using `src/steps.ts` and the visible reference code.
4. Run `npm start`, then launch your registered app from nav-epj.

The host registers `tokenEndpointAuthMethod: client_secret_basic`,
`grantTypes: [authorization_code]`, launch URL `http://localhost:3100/launch`,
redirect URL `http://localhost:3100/callback`, `launchMode: tab`, and the matching secret.
`client_secret_post` is not supported.

For the public/private keys supplied on the day, use the `private-key-jwt` variant instead.
`npm run start:reference` runs the completed client-secret example.

Already have a working launch? [PROGRESSION.md](./PROGRESSION.md) explains how to retain it
and change only token authentication. [EXERCISES.md](./EXERCISES.md) offers feature ideas,
not tests to pass.
