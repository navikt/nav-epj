package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.condition.conditionRoutes
import no.nav.helse.fhir.documentreference.DocumentReferenceId
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.documentreference.documentReferenceRoutes
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.encounter.encounterRoutes
import no.nav.helse.fhir.organization.OrganizationId
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.organization.organizationRoutes
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.patientRoutes
import no.nav.helse.fhir.practitioner.PractitionerId
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitioner.pracitionerRoutes

fun Application.configureFhirModule() {
  val conditionService: ConditionService by dependencies
  val encounterService: EncounterService by dependencies
  val organizationService: OrganizationService by dependencies
  val patientService: PatientService by dependencies
  val practitionerService: PractitionerService by dependencies
  val documentReferenceService: DocumentReferenceService by dependencies
  val fhirJson = FhirR4Json()
  val fhirContentType = ContentType("application", "fhir+json")

  routing {
    authenticate("smart-access-token") {
      conditionRoutes(conditionService, fhirJson, fhirContentType)
      encounterRoutes(encounterService, fhirJson, fhirContentType)
      organizationRoutes(organizationService, fhirJson, fhirContentType)
      patientRoutes(patientService, fhirJson, fhirContentType)
      pracitionerRoutes(practitionerService, fhirJson, fhirContentType)
      documentReferenceRoutes(documentReferenceService, fhirJson, fhirContentType)
    }
  }
}

fun ApplicationCall.encounterId(): EncounterId = EncounterId(uuidParameter("encounter"))

fun ApplicationCall.documentReferenceId(): DocumentReferenceId =
  DocumentReferenceId(uuidParameter("documentreferenceId"))

fun ApplicationCall.organizationId(): OrganizationId =
  OrganizationId(stringParameter("organizationId"))

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
  PatientInputId(uuidReferenceParameter("subject"))

fun ApplicationCall.encounterReferenceId(): EncounterId =
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
private fun ApplicationCall.uuidReferenceParameter(name: String): Uuid {
  val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")
  val id = value.substringAfterLast('/')

  return try {
    Uuid.parse(id)
  } catch (exception: IllegalArgumentException) {
    throw BadRequestException("Parameteren '$name' er ikke en gyldig UUID", exception)
  }
}
