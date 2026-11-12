package no.nav.helse.core

import glide.api.GlideClient
import glide.api.models.configuration.GlideClientConfiguration
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.ehr.clinic.ClinicRepository
import no.nav.helse.ehr.clinic.ClinicService
import no.nav.helse.ehr.consultation.ClinicalDocumentRepository
import no.nav.helse.ehr.consultation.ConsultationRepository
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.measurement.MeasurementRepository
import no.nav.helse.ehr.measurement.MeasurementService
import no.nav.helse.ehr.patient.PatientRepository
import no.nav.helse.ehr.patient.PatientService as EhrPatientService
import no.nav.helse.ehr.practitioner.PractitionerRepository
import no.nav.helse.ehr.practitioner.PractitionerService as EhrPractitionerService
import no.nav.helse.ehr.sessionorg.FastlegelisteRepository
import no.nav.helse.ehr.sessionorg.SessionOrgChoiceStore
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.ehr.sessionorg.SessionOrgService
import no.nav.helse.ehr.sessionorg.ValkeySessionOrgChoiceStore
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.observation.ObservationService
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitionerrole.PractitionerRoleService
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseRepository
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseService
import no.nav.helse.smart.security.ClientAssertionVerifier
import no.nav.helse.smart.security.ClientJwksSetProvider
import no.nav.helse.smart.security.RemoteClientJwksSetProvider
import no.nav.helse.smart.security.SmartKeys
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
        provide<ClientJwksSetProvider> { RemoteClientJwksSetProvider() }
        provide(ClientAssertionVerifier::class)
        provide<SmartKeys> { SmartKeys(resolve<Environment>().smart.privateKeyJwk) }

        provide(PatientRepository::class)
        provide(PractitionerRepository::class)
        provide(FastlegelisteRepository::class)
        provide(ConsultationRepository::class)
        provide(ClinicalDocumentRepository::class)
        provide(QuestionnaireResponseRepository::class)
        provide(ClinicRepository::class)
        provide(MeasurementRepository::class)

        provide(EhrPractitionerService::class)
        provide(ConsultationService::class)
        provide(EhrPatientService::class)
        provide(ClinicService::class)
        provide(MeasurementService::class)

        provide<SessionOrgChoiceStore> { ValkeySessionOrgChoiceStore(resolve()) }
        provide(SessionOrgResolver::class)
        provide(SessionOrgService::class)

        provide(ConditionService::class)
        provide(DocumentReferenceService::class)
        provide(EncounterService::class)
        provide(ObservationService::class)
        provide(OrganizationService::class)
        provide(PatientService::class)
        provide(PractitionerService::class)
        provide(PractitionerRoleService::class)
        provide(QuestionnaireResponseService::class)
    }
}
