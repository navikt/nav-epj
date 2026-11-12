package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.core.Environment
import no.nav.helse.fhir.capabilitystatement.capabilityStatementRoutes
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.condition.conditionRoutes
import no.nav.helse.fhir.documentreference.DocumentReferenceHandler
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.encounter.encounterRoutes
import no.nav.helse.fhir.observation.ObservationId
import no.nav.helse.fhir.observation.ObservationService
import no.nav.helse.fhir.observation.observationRoutes
import no.nav.helse.fhir.organization.OrganizationId
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.organization.organizationRoutes
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.patientRoutes
import no.nav.helse.fhir.practitioner.PractitionerId
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitioner.pracitionerRoutes
import no.nav.helse.fhir.practitionerrole.PractitionerRoleService
import no.nav.helse.fhir.practitionerrole.practitionerRoleRoutes
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseHandler
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseService

fun Application.configureFhirModule() {
    routing { fhirRoutes() }
}

fun Route.fhirRoutes() {
    val conditionService: ConditionService by application.dependencies
    val encounterService: EncounterService by application.dependencies
    val observationService: ObservationService by application.dependencies
    val organizationService: OrganizationService by application.dependencies
    val patientService: PatientService by application.dependencies
    val practitionerService: PractitionerService by application.dependencies
    val practitionerRoleService: PractitionerRoleService by application.dependencies
    val documentReferenceService: DocumentReferenceService by application.dependencies
    val questionnaireResponseService: QuestionnaireResponseService by application.dependencies
    val environment: Environment by application.dependencies
    val fhirJson = FhirR4Json()
    val fhirContentType = ContentType("application", "fhir+json")

    val codec = FhirCodec(fhirJson)
    val documentReferenceHandler =
        DocumentReferenceHandler(documentReferenceService, environment.smart.fhirServerUrl)
    val questionnaireResponseHandler =
        QuestionnaireResponseHandler(questionnaireResponseService, environment.smart.fhirServerUrl)

    capabilityStatementRoutes(fhirJson, fhirContentType)
    authenticate("smart-access-token") {
        conditionRoutes(conditionService, fhirJson, fhirContentType)
        encounterRoutes(encounterService, fhirJson, fhirContentType)
        observationRoutes(
            observationService,
            fhirJson,
            fhirContentType,
            environment.smart.fhirServerUrl,
        )
        organizationRoutes(organizationService, fhirJson, fhirContentType)
        patientRoutes(patientService, fhirJson, fhirContentType)
        pracitionerRoutes(practitionerService, fhirJson, fhirContentType)
        practitionerRoleRoutes(practitionerRoleService, fhirJson, fhirContentType)
        fhirResourceRoutes(documentReferenceHandler, codec, fhirContentType)
        fhirResourceRoutes(questionnaireResponseHandler, codec, fhirContentType)
        fhirBatchRoutes(
            FhirBatchProcessor(listOf(documentReferenceHandler, questionnaireResponseHandler)),
            codec,
            fhirContentType,
        )
    }
}

fun ApplicationCall.encounterId(): EncounterId = EncounterId(uuidParameter("encounter"))

fun ApplicationCall.observationId(): ObservationId = ObservationId(uuidParameter("observation"))

fun ApplicationCall.organizationId(): OrganizationId =
    OrganizationId(uuidParameter("organizationId"))

fun ApplicationCall.patientInputId(): PatientInputId = PatientInputId(uuidParameter("subject"))

fun ApplicationCall.practitionerId(): PractitionerId =
    PractitionerId(stringParameter("practitionerId"))

private fun ApplicationCall.uuidParameter(name: String): Uuid {
    val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")

    return try {
        Uuid.parse(value)
    } catch (exception: IllegalArgumentException) {
        throw BadRequestException("Parameteren '$name' er ikke en gyldig UUID", exception)
    }
}

fun ApplicationCall.patientReferenceInputId(): PatientInputId =
    PatientInputId(parameters.uuidReferenceParameter("subject"))

fun ApplicationCall.encounterReferenceId(): EncounterId = parameters.encounterReferenceId()

fun Parameters.encounterReferenceId(): EncounterId =
    EncounterId(uuidReferenceParameter("encounter"))

private fun ApplicationCall.stringParameter(name: String): String {
    val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")
    return value
}

/**
 * Parses a FHIR search reference parameter such as `subject=Patient/123` or
 * `encounter=Encounter/456`. FHIR R4 reference search parameters are conventionally sent as
 * `ResourceType/id` (search.html #reference), not a bare id, so any such prefix must be stripped
 * before parsing the UUID.
 */
private fun Parameters.uuidReferenceParameter(name: String): Uuid {
    val value = this[name] ?: throw BadRequestException("Mangler parameteren '$name'")
    return parseUuidReference(value, name)
}

private fun parseUuidReference(value: String, name: String): Uuid {
    val id = value.substringAfterLast('/')

    return try {
        Uuid.parse(id)
    } catch (exception: IllegalArgumentException) {
        throw BadRequestException("Parameteren '$name' er ikke en gyldig UUID", exception)
    }
}

/**
 * Resolves the patient compartment for a search accepting either `subject` or `patient` as the FHIR
 * R4 reference parameter, since both are used interchangeably across SMART clients. Both may be
 * given together only if they agree on the same patient; disagreement is rejected explicitly rather
 * than silently preferring one of them.
 */
fun ApplicationCall.patientOrSubjectReferenceInputId(): PatientInputId =
    parameters.patientOrSubjectReferenceInputId()

fun Parameters.patientOrSubjectReferenceInputId(): PatientInputId {
    val subjectId = this["subject"]?.let { parseUuidReference(it, "subject") }
    val patientId = this["patient"]?.let { parseUuidReference(it, "patient") }
    if (subjectId == null && patientId == null) {
        throw BadRequestException("Mangler parameteren 'subject' eller 'patient'")
    }
    if (subjectId != null && patientId != null && subjectId != patientId) {
        throw BadRequestException("Parameterne 'subject' og 'patient' peker på ulike pasienter")
    }
    return PatientInputId(subjectId ?: patientId!!)
}
