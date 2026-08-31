package no.nav.helse.core

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.epj.helsepersonell.HelsepersonellRepository
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.konsultasjon.KonsultasjonRepository
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.legekontor.LegekontorRepository
import no.nav.helse.epj.legekontor.LegekontorService
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.epj.pdl.PdlArrowed
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitionerrole.PractitionerRoleService
import no.nav.helse.smart.security.ClientAssertionVerifier
import no.nav.helse.smart.security.ClientJwksSetProvider
import no.nav.helse.smart.security.RemoteClientJwksSetProvider
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.smart.valkey.createGlideClient
import no.nav.helse.smart.valkey.createGlideClientConfiguration
import no.nav.tsm.ktor.auth.texas.Texas
import no.nav.tsm.pdl.plugin.PdlPlugin

fun Application.configureDependencies() {
  val config = environment.config
  install(PdlPlugin)

  dependencies {
    provide<Environment> { initEnvironment(config) }
    provide<GlideClientConfiguration> { createGlideClientConfiguration(resolve()) }
    provide<GlideClient> { createGlideClient(resolve()) }
    provide<HttpClient> { configureBaseHttpClient() }
    provide(Texas::class)

    provide(ValkeyService::class)
    provide<ClientJwksSetProvider> { RemoteClientJwksSetProvider() }
    provide(ClientAssertionVerifier::class)

    provide(PdlArrowed::class)

    provide(PasientRepository::class)
    provide(HelsepersonellRepository::class)
    provide(KonsultasjonRepository::class)
    provide(LegekontorRepository::class)

    provide(HelsepersonellService::class)
    provide(KonsultasjonService::class)
    provide(PasientService::class)
    provide(LegekontorService::class)

    provide(ConditionService::class)
    provide(DocumentReferenceService::class)
    provide(EncounterService::class)
    provide(OrganizationService::class)
    provide(PatientService::class)
    provide(PractitionerService::class)
    provide(PractitionerRoleService::class)
  }

}

private fun configureBaseHttpClient(): HttpClient = HttpClient(Apache5) {}
