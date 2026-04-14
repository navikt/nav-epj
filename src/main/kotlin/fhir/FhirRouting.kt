package no.nav.helse.fhir

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import no.nav.helse.UserSession
import no.nav.helse.fhir.repository.StubPatientRepository

private val fhirJson = FhirR4Json()
private const val FHIR_CONTENT_TYPE = "application/fhir+json"

fun Application.configureFhirRouting() {
  val patientService = PatientService(StubPatientRepository())

  routing {
    route("/fhir") {
      get("/Patient/{id}") {
        if (!call.isAuthenticated()) {
          call.respondRedirect("/login")
          return@get
        }

        val id = call.parameters["id"]
          ?: return@get call.respondText("Missing patient id", status = HttpStatusCode.BadRequest)

        val patient = patientService.getPatient(id)
        if (patient != null) {
          call.respondFhir(patient)
        } else {
          call.respondText("Patient not found", status = HttpStatusCode.NotFound)
        }
      }

      get("/Patient") {
        if (!call.isAuthenticated()) {
          call.respondRedirect("/login")
          return@get
        }

        val patients = patientService.getAllPatients()
        val bundle = Bundle(
          type = Enumeration(value = Bundle.BundleType.Searchset),
          entry = patients.map { patient ->
            Bundle.Entry(
              fullUrl = Uri(value = "Patient/${patient.id}"),
              resource = patient
            )
          }
        )
        call.respondFhir(bundle)
      }
    }
  }
}

private fun ApplicationCall.isAuthenticated(): Boolean {
  val session = sessions.get<UserSession>()
  return session?.accessToken != null
}

private suspend inline fun <reified T : Resource> ApplicationCall.respondFhir(resource: T) {
  response.header(HttpHeaders.ContentType, FHIR_CONTENT_TYPE)
  respondText(fhirJson.encodeToString(resource), ContentType.Application.Json)
}
