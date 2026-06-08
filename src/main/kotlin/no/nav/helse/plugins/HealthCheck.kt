package no.nav.helse.plugins

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureHealthCheck() {
  routing {
    get("/internal/health/alive") { call.respondText("alive") }
    get("/internal/health/ready") { call.respondText("ready") }
  }
}
