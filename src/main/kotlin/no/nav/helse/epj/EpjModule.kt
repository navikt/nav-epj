package no.nav.helse.epj

import io.ktor.server.application.*
import no.nav.helse.epj.api.configureEpjRouting

fun Application.configureEpjModule() {
  configureEpjDependencies()
  configureEpjRouting()
}
