# NAV Test EPJ

Der ingen skulle tru

## Run locally with wonderwall and mock-oauth2-server

You can run the application with:

- wonderwall
- redis (for wonderwall)
- mock-oauth2-server

By first starting the development server with `yarn dev` (will run on :3000).

Start the rest of the containers using `docker compose up`. Wonderwall will run on :3005 and proxy all requests to :3000.

You can visit http://localhost:3005/api/whoami to debug the current user.
