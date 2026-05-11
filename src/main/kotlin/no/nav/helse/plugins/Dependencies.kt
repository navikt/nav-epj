package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.core.Environment
import no.nav.helse.core.initializeEnvironment
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.condition.repository.ConditionRepositoryImpl
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.documentreference.repository.StubDocumentReferenceRepository
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.encounter.repository.EncounterRepositoryImpl
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.organization.repository.OrganizationRepositoryImpl
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.repository.StubPatientRepository
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitioner.repository.PractitionerRepositoryImpl

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
      DocumentReferenceService(StubDocumentReferenceRepository())
    }
  }
}
