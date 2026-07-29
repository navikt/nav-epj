package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import no.nav.helse.fhir.Condition.ConditionService
import no.nav.helse.fhir.Condition.conditionRoutes
import no.nav.helse.fhir.Encounter.EncounterService
import no.nav.helse.fhir.Encounter.encounterRoutes
import no.nav.helse.fhir.Organization.OrganizationService
import no.nav.helse.fhir.Organization.organizationRoutes
import no.nav.helse.fhir.Patient.PatientService
import no.nav.helse.fhir.Patient.patientRoutes
import no.nav.helse.fhir.Practitioner.PractitionerService
import no.nav.helse.fhir.Practitioner.pracitionerRoutes

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
