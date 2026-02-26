package no.nav.tsm.frontend

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.pebbletemplates.pebble.loader.ClasspathLoader

fun Application.epjFrontendModule() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
        })
    }

    routing {
        staticResources("/static", "static")

        get("/") {
            call.respond(
                PebbleContent("main.html", mapOf("test" to "Hello from Pebble!"))
            )
        }
    }
}
