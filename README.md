# dr-zara

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                       | Description                                                    |
|------------------------------------------------------------|----------------------------------------------------------------|
| [Routing](https://start.ktor.io/p/routing)                 | Provides a structured routing DSL                              |
| [Authentication](https://start.ktor.io/p/auth)             | Provides extension point for handling the Authorization header |
| [Authentication OAuth](https://start.ktor.io/p/auth-oauth) | Handles OAuth Bearer authentication scheme                     |
| [Authentication JWT](https://start.ktor.io/p/auth-jwt)     | Handles JSON Web Token (JWT) bearer authentication scheme      |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
|-----------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Building and running frontend locally

Frontend can be run separate from backend when developing locally. To do this, run the following commands in the terminal:

`cd frontend`
`yarn dev`

TODO: Add functionality to proxy API calls to backend when running frontend locally.

For the frontend to be served with ktor, the dist folder needs to be copied into the `resources` folder in the backend.

TODO: Add github action to build and copy dist folder into `resources` when building backend for nais envinronments.