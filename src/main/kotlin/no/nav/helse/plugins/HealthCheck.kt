package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHealthCheck() {
  routing {
    get("/internal/health/alive") { call.respondText("alive") }
    get("/internal/health/ready") { call.respondText("ready") }
  }
}
