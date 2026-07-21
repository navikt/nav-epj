package no.nav.helse.core

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.fhir.FhirService
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.smart.valkey.createGlideClient
import no.nav.helse.smart.valkey.createGlideClientConfiguration

fun Application.configureDependencies() {
  val config = environment.config
  dependencies {
    provide<Environment> { initEnvironment(config) }
    provide<GlideClientConfiguration> { createGlideClientConfiguration(resolve()) }
    provide<GlideClient> { createGlideClient(resolve()) }
    provide(FhirService::class)
    provide(ValkeyService::class)
  }
}
