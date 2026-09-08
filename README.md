# nav-epj

## Overview

This application simulates an EHR system that launches applications using SMART on FHIR and exposes
healthcare data through a FHIR API.

## Local Development

### Prerequisites

Before running the application, make sure you have the following installed:

- [Node.js](https://nodejs.org/en/) (LTS)
- [Yarn](https://yarnpkg.com/) (`corepack enable`)
- [Docker](https://www.docker.com/)
- [Java 25](https://openjdk.org/projects/jdk/25/)


### Running the application locally

In addition to Node.js, Yarn and Docker, running the app locally requires PostgreSQL and Valkey.
Start these with:

```bash                                                                                                                                                                                                                                                                             ┃
docker-compose up -d
```

The frontend and backend must run in separate terminal windows.

### Frontend

Navigate to the frontend directory and start the development server:

```bash
cd frontend
yarn install
yarn dev
```

### Backend

From the backend directory, start the application using Gradle:

```bash
./gradlew runLocal
```

### Testing the SMART launch flow with SMART on FHIR Validator

To test the SMART launch flow locally,
[the SMART on FHIR Validator](https://github.com/navikt/smart-on-fhir-validator) must also be
running. Follow the instructions in the validator repository to start it before testing the launch
flow.

### HOW-TO authenticate SMART clients based on auth method

Read [this guide](./docs/smart-client-authentication-guide.md) for instructions and code examples on
how to make your SMART on FHIR application work with nav-epj.

### Finding new available dependencies
``` bash
./gradlew dependencyUpdates
```

### Upgrading the Gradle wrapper version to latest
``` bash
./gradlew :wrapper --gradle-version latest
```

### Contact

This project is maintained by [navikt/tsm](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/nav-epj/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-symfoni](https://nav-it.slack.com/archives/C07MY3KCDS5)