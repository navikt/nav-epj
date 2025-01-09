# NAV Test EPJ

Der ingen skulle tru

## Set up local development

### Prerequisites

(Use mise? `mise i` to install the required prerequisites)

- [Node.js](https://nodejs.org/en/) v22 (LTS)
- [Yarn](https://yarnpkg.com/) (`corepack enable`)

This project relies on a Github PAT with `package:read` available as `NPM_AUTH_TOKEN`-environment variable for
authenticated access to the Github Package Registry.

### Develop

Run dev server with `yarn dev`

## Run locally with wonderwall and mock-oauth2-server

You can run the application with:

- wonderwall
- redis (for wonderwall)
- mock-oauth2-server

By first starting the development server with `yarn dev` (will run on :3000).

Start the rest of the containers using `docker compose up`. Wonderwall will run on :3005 and proxy all requests to :3000.

You can visit http://localhost:3005/api/whoami to debug the current user.
