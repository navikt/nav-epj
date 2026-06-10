package no.nav.helse.epj

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.epj.db.PasientRepository

fun Application.configureEpjDependencies() {
  dependencies {
    provide(PasientRepository::class)
    provide(EpjService::class)
  }
}
