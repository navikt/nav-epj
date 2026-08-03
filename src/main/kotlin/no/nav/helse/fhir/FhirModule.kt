package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.condition.conditionRoutes
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.encounter.encounterRoutes
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.organization.organizationRoutes
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.patientRoutes
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitioner.pracitionerRoutes

fun Application.configureFhirModule() {
  val conditionService: ConditionService by dependencies
  val encounterService: EncounterService by dependencies
  val organizationService: OrganizationService by dependencies
  val patientService: PatientService by dependencies
  val practitionerService: PractitionerService by dependencies
  val fhirJson = FhirR4Json()
  val fhirContentType = ContentType("application", "fhir+json")

  routing {
    authenticate("smart-access-token") {
      conditionRoutes(conditionService, fhirJson, fhirContentType)
      encounterRoutes(encounterService, fhirJson, fhirContentType)
      organizationRoutes(organizationService, fhirJson, fhirContentType)
      patientRoutes(patientService, fhirJson, fhirContentType)
      pracitionerRoutes(practitionerService, fhirJson, fhirContentType)
    }
  }
}
