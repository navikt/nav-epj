package no.nav.tsm.frontend

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.pebbletemplates.pebble.loader.ClasspathLoader
import no.nav.tsm.frontend.wonderwall.Wonderwall

fun Application.epjFrontendModule() {
    configureDependencies()
    configurePepple()

    val wonderwall: Wonderwall by dependencies

    routing {
        staticResources("/static", "static")
        landingPage(wonderwall)
    }
}

fun Routing.landingPage(wonderwall: Wonderwall) {
    get("/") {
        call.respond(
            PebbleContent("main.html", mapOf("content" to "Hello from Pebble! You are ${wonderwall.user().hpr}"))
        )
    }
}

fun Application.configurePepple() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
        })
    }
}
