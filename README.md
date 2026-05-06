# dr-zara

## Features

Here's a list of features included in this project:

| Name                                                                             | Description                                                    |
|----------------------------------------------------------------------------------|----------------------------------------------------------------|
| [Routing](https://start.ktor.io/p/routing)                                       | Provides a structured routing DSL                              |
| [Authentication](https://start.ktor.io/p/auth)                                   | Provides extension point for handling the Authorization header |
| [Authentication OAuth](https://start.ktor.io/p/auth-oauth)                       | Handles OAuth Bearer authentication scheme                     |
| [Authentication JWT](https://start.ktor.io/p/auth-jwt)                           | Handles JSON Web Token (JWT) bearer authentication scheme      |
| [Dependency Injection](https://start.ktor.io/p/server-dependency-injection.html) | Provides dependency injection on the server                    |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                                                             | Description                                                          |
|----------------------------------------------------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                                                                 | Run the tests                                                        |
| `./gradlew build`                                                                | Build everything                                                     |
| `./gradlew buildFatJar`                                                          | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                                                           | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry`                                          | Publish the docker image locally                                     |
| `./gradlew run --args='-config=application.yaml -config=application-local.yaml'` | Run the server with local config                                     |
| `./gradlew runDocker`                                                            | Run using the local docker image                                     |

## Building and running frontend locally

Frontend can be run separate from backend when developing locally. To do this, run the following
commands in the terminal:

`cd frontend`
`yarn dev`

TODO: Add functionality to proxy API calls to backend when running frontend locally.

For the frontend to be served with ktor, the dist folder needs to be copied into the `resources`
folder in the backend.

TODO: Add github action to build and copy dist folder into `resources` when building backend for
nais envinronments.