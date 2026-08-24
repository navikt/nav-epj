package no.nav.helse

import io.ktor.server.application.*
import no.nav.helse.core.configureDependencies
import no.nav.helse.core.db.configureDatabases
import no.nav.helse.epj.configureEpjModule
import no.nav.helse.fhir.configureFhirModule
import no.nav.helse.helseId.configureHelseId
import no.nav.helse.plugins.configureCallLogging
import no.nav.helse.plugins.configureCors
import no.nav.helse.plugins.configureHealthCheck
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.api.configureSmartRouting
import no.nav.helse.smart.security.configureSmartSecurity

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
  configureSerialization()
  configureDependencies()
  configureStatusPages()

  configureDatabases()
  configureHealthCheck()
  configureCors()
  configureCallLogging()

  configureHelseId()
  configureSmartSecurity()
  configureSmartRouting()
  configureFhirModule()

  configureEpjModule()
}
