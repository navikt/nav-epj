package no.nav.helse.fhir

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.fhir.Condition.ConditionService
import no.nav.helse.fhir.DocumentReference.DocumentReferenceService
import no.nav.helse.fhir.Encounter.EncounterService
import no.nav.helse.fhir.Organization.OrganizationService
import no.nav.helse.fhir.Patient.PatientService
import no.nav.helse.fhir.Practitioner.PractitionerService

fun Application.configureFhirDependencies() {
  dependencies {

    provide(ConditionService::class)
    provide(DocumentReferenceService::class)
    provide(EncounterService::class)
    provide(OrganizationService::class)
    provide(PatientService::class)
    provide(PractitionerService::class)

    provide<HttpClient> { initEpjClient() }
  }
}
