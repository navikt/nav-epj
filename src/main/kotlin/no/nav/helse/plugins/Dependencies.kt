package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.core.Environment
import no.nav.helse.core.initEnvironment

fun Application.configureDependencies() {
  val config = environment.config
  dependencies { provide<Environment> { initEnvironment(config) } }
}
