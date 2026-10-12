# Public EHR-launch starter

Build a SMART app and launch it from nav-epj. This variant uses `none` at the token
endpoint: no client secret or key is required, but PKCE still is.

1. Use Node 22 and run `npm ci`.
2. Follow [GUIDE.md](./GUIDE.md) to configure the client registration supplied by the host.
3. Build your app using `src/steps.ts` as launch scaffolding and `src/reference.ts` for help.
4. Run `npm start`, log into nav-epj, select a synthetic patient and launch your app.

The host registers `tokenEndpointAuthMethod: none`, `grantTypes: [authorization_code]`,
launch URL `http://localhost:3100/launch`, redirect URL `http://localhost:3100/callback`
and `launchMode: tab`, or agrees different addresses with your team.

If you are using the host-supplied public/private key pair, choose the `private-key-jwt`
starter instead. `npm run start:reference` runs this variant's complete example.

See [ideas for your app](./EXERCISES.md) and [how to keep a working launch](./PROGRESSION.md).
The aim is a working app in nav-epj, not passing a participant test suite.
