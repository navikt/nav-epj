package no.nav.helse

import io.ktor.server.application.*
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import no.nav.helse.fhir.configureFhirRouting
import no.nav.helse.plugins.configureSerialization

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  configureSerialization()
  install(Sessions) {
    cookie<UserSession>("USER_SESSION")
  }

  configureSecurity()
  configureRouting()
  configureFhirRouting()

  if (environment.config.propertyOrNull("ktor.environment")?.getString() == "local") {
    configureOidcStub()
  }
}
