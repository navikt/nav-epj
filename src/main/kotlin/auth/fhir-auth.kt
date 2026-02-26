package no.nav.tsm.auth

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.fhirAuthModule() {
    routing {
        route("/auth") {
            get("/.well-known/smart-configuration") {
                call.respondText("Hello FHIR!")
            }.describe {
                summary = "Get SMART on FHIR configuration"
            }
        }
    }
}
