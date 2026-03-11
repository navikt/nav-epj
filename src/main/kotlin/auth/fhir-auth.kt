package no.nav.tsm.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.fhirAuthModule() {
    routing {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
            allowHeader(HttpHeaders.Authorization)

            // TODO: Don't anyHost
            anyHost()
        }

        get("/.well-known/smart-configuration") {
            call.respond(
                HttpStatusCode.OK, mapOf(
                    "issuer" to "http://localhost:8080",
                    "jwks_uri" to "http://localhost:8080/keys",
                    "authorization_endpoint" to "http://localhost:8080/auth/authorize",
                    "token_endpoint" to "http://localhost:8080/auth/token",
                )
            )
        }.describe {
            summary = "Get SMART on FHIR configuration"
        }


        route("/auth") {


        }
    }
}
