package no.nav.helse.smart

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.fhir.FhirService
import no.nav.helse.smart.db.InMemoryLaunchStore
import no.nav.helse.smart.db.LaunchStore

fun Application.configureSmartDependencies() {
  dependencies {
    provide(FhirService::class)
    provide<LaunchStore> { InMemoryLaunchStore() } // TODO replace with valkey
  }
}
