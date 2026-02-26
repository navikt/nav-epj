package no.nav.tsm.plugins

import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.routing.*

fun Application.configureOpenAPI() {
    routing {
        openAPI(path = "openapi") {
            info = OpenApiInfo(title = "Nav EPJ", version = "1.0.0")
        }
    }
}
