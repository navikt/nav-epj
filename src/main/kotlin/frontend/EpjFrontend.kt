package no.nav.tsm.frontend

import frontend.user.HelseIdPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.pebbletemplates.pebble.loader.ClasspathLoader
import frontend.user.loggedInUser
import io.ktor.server.auth.principal

fun Application.epjFrontendModule() {
    configurePepple()

    routing {
        staticResources("/static", "static")
        authenticate("wonderwall-helseid") {
            landingPage()
        }
    }
}

fun Route.landingPage() {
    get("/") {
        val user = loggedInUser()

        call.respond(
            PebbleContent("main.html", mapOf("content" to "Hello from Pebble! You are ${user.hpr}"))
        )
    }
    get("/debug-user") {
        val principal = this.call.principal<HelseIdPrincipal>()

        call.respond<HelseIdPrincipal>(principal ?: error("User not found in principal"))
    }
}

fun Application.configurePepple() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
        })
    }
}
