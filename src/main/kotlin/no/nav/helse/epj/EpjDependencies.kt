package no.nav.helse.epj

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository

fun Application.configureEpjDependencies() {
  dependencies {
    provide(PasientRepository::class)
    provide(HelsepersonellRepository::class)
    provide(KonsultasjonRepository::class)
    provide(EpjService::class)
    provide<ClinicianContextStore> { InMemoryClinicianContextStore() } // TODO replace with valkey
  }
}
