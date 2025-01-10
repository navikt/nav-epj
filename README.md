# NAV Test EPJ

Der ingen skulle tru

## Set up local development

### Prerequisites

(Use mise? `mise i` to install the required prerequisites)

- [Node.js](https://nodejs.org/en/) v22 (LTS)
- [Yarn](https://yarnpkg.com/) (`corepack enable`)
- [Docker](https://www.docker.com/)

This project relies on a Github PAT with `package:read` available as `NPM_AUTH_TOKEN`-environment variable for
authenticated access to the Github Package Registry.

### Develop

Run dev server with `yarn dev`

## Run locally with wonderwall

### Wonderwall + mock-oauth2-server

You can run the application with:

- wonderwall
- redis (for wonderwall)
- mock-oauth2-server

By first starting the development server with `yarn dev` (will run on :3000).

Start the rest of the containers using `docker compose -f docker-compose.mock.yml up`. Wonderwall will run on :3005 and proxy all requests to :3000.

You can visit http://localhost:3005/api/whoami to debug the current user.

### Wonderwall + nav-epj-auth-server (image)

To run the application with wonderwall and nav-epj-auth-server you will first need to set up access to Google Artifact Registry.

1. Log in to gcloud CLI: `gcloud auth login --update-adc`
2. Configure docker authentication: `gcloud auth configure-docker europe-north1-docker.pkg.dev` (only needed once)

You should now be able to run `docker compose up` which will start wonederwall, redis, nav-epj-auth-server (from GAR).

### Wonderwall + nav-epj-auth-server (development mode)

If you are running nav-epj-auth-server in development mode, you can run only wonderwall(+redis):

`docker compose -f docker-compose-wonderwall.yml up`
