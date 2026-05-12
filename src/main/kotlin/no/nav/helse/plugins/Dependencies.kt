package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.core.Environment
import no.nav.helse.core.initializeEnvironment
import no.nav.helse.fhir.condition.ConditionRepositoryImpl
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.documentreference.DocumentReferenceRepositoryImpl
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.encounter.EncounterRepositoryImpl
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.organization.OrganizationRepositoryImpl
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.StubPatientRepository
import no.nav.helse.fhir.practitioner.PractitionerRepositoryImpl
import no.nav.helse.fhir.practitioner.PractitionerService

fun Application.configureDependencies() {
  val env = initializeEnvironment(environment.config)
  dependencies {
    provide<Environment> { env }
    provide<PatientService> { PatientService(StubPatientRepository()) }
    provide<OrganizationService> { OrganizationService(OrganizationRepositoryImpl()) }
    provide<EncounterService> { EncounterService(EncounterRepositoryImpl()) }
    provide<ConditionService> { ConditionService(ConditionRepositoryImpl()) }
    provide<PractitionerService> { PractitionerService(PractitionerRepositoryImpl()) }
    provide<DocumentReferenceService> {
      DocumentReferenceService(DocumentReferenceRepositoryImpl())
    }
  }
}
