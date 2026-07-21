package no.nav.helse

import io.ktor.server.application.*
import no.nav.helse.core.configureDependencies
import no.nav.helse.core.db.configureDatabases
import no.nav.helse.epj.configureEpjModule
import no.nav.helse.fhir.api.configureFhirRouting
import no.nav.helse.helseIdAuth.configureHelseIdAuth
import no.nav.helse.plugins.configureCors
import no.nav.helse.plugins.configureHealthCheck
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.smart.api.configureSmartRouting
import no.nav.helse.smart.security.configureSmartSecurity

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
  configureDependencies()
  configureSerialization()
  configureDatabases()
  configureHealthCheck()
  configureCors()

  configureHelseIdAuth()
  configureSmartSecurity()
  configureSmartRouting()
  configureFhirRouting()

  configureEpjModule()
}
