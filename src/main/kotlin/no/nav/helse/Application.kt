package no.nav.helse

import database.configureDatabase
import io.ktor.server.application.*
import no.nav.helse.auth.configureSecurity
import no.nav.helse.auth.stub.configureOidcStub
import no.nav.helse.fhir.configureFhirRouting
import no.nav.helse.plugins.configureDependencies
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureSession

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
  configureDependencies()
  configureSerialization()
  configureSession()
  configureSecurity()
  configureRouting()
  configureFhirRouting()
  configureDatabase()

  configureOidcStub()
  //  val env: Environment by dependencies
  //  if (env.runtime == Runtime.LOCAL) {
  //  }
}
