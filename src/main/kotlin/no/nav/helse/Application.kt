package no.nav.helse

import io.ktor.server.application.*
import no.nav.helse.core.db.configureDatabases
import no.nav.helse.epj.configureEpjModule
import no.nav.helse.helseIdAuth.configureHelseIdAuth
import no.nav.helse.plugins.configureDependencies
import no.nav.helse.plugins.configureHealthCheck
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.smartAuth.configureSmartAuth

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
  configureSerialization()
  configureDatabases()
  configureHealthCheck()

  configureHelseIdAuth()
  configureSmartAuth()

  configureEpjModule()
  configureDependencies()
}
