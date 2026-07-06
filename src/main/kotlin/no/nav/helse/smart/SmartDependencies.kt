package no.nav.helse.smart

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.fhir.FhirService
import no.nav.helse.smart.db.AuthCodeContext
import no.nav.helse.smart.db.InMemorySingleUseStore
import no.nav.helse.smart.db.LaunchContext
import no.nav.helse.smart.db.SingleUseStore

fun Application.configureSmartDependencies() {
  dependencies {
    provide(FhirService::class)
    provide<SingleUseStore<LaunchContext>> { InMemorySingleUseStore() } // TODO replace with valkey
    provide<SingleUseStore<AuthCodeContext>> {
      InMemorySingleUseStore()
    } // TODO replace with valkey
  }
}
