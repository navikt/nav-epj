package no.nav.helse.core

// import no.nav.helse.fhir.documentreference.DocumentReferenceService
import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import io.ktor.client.*
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
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.initEpjClient
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.practitioner.PractitionerService
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
    provide(LegekontorRepository::class)

    provide(HelsepersonellService::class)
    provide(KonsultasjonService::class)
    provide(PasientService::class)
    provide(LegekontorService::class)

    provide(ConditionService::class)
    // provide(DocumentReferenceService::class)
    provide(EncounterService::class)
    provide(OrganizationService::class)
    provide(PatientService::class)
    provide(PractitionerService::class)

    provide<HttpClient> { initEpjClient(resolve<Environment>().epj.baseUrl) }
  }
}
