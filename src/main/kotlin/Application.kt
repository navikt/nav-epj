package no.nav.helse

import io.ktor.server.application.*
import no.nav.helse.auth.configureSecurity
import no.nav.helse.auth.stub.configureOidcStub
import no.nav.helse.fhir.configureFhirRouting
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureSession

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
  configureSerialization()
  configureSession()
  configureSecurity()
  configureRouting()
  configureFhirRouting()

  if (environment.config.propertyOrNull("ktor.environment")?.getString() == "local") {
    configureOidcStub()
  }
}
