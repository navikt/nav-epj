package no.nav.helse

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import no.nav.helse.fhir.configureFhirRouting

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  install(ContentNegotiation) {
    json()
  }

  install(Sessions) {
    cookie<UserSession>("USER_SESSION")
  }

  configureSecurity()
  configureRouting()
  configureFhirRouting()

  if (environment.config.property("ktor.environment").getString() == "local") {
    configureOidcStub()
  }
}
