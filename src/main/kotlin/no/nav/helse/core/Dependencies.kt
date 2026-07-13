package no.nav.helse.core

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.configureDependencies() {
  val config = environment.config
  dependencies { provide<Environment> { initEnvironment(config) } }
}
