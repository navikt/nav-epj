package no.nav.helse.core

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.epj.helsepersonell.HelsepersonellRepository
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.konsultasjon.KonsultasjonRepository
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.smart.valkey.createGlideClient
import no.nav.helse.smart.valkey.createGlideClientConfiguration

fun Application.configureDependencies() {
  val config = environment.config
  dependencies {
    provide<Environment> { initEnvironment(config) }
    provide<GlideClientConfiguration> { createGlideClientConfiguration(resolve()) }
    provide<GlideClient> { createGlideClient(resolve()) }

    provide(ValkeyService::class)

    provide(PasientRepository::class)
    provide(HelsepersonellRepository::class)
    provide(KonsultasjonRepository::class)
    provide(HelsepersonellService::class)
    provide(KonsultasjonService::class)
    provide(PasientService::class)
  }
}
