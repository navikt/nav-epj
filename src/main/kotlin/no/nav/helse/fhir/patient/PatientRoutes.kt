package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger

@OptIn(ExperimentalUuidApi::class)
fun Route.patientRoutes(
  patientService: PatientService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  val log = logger()

  route("/fhir") {
    get("/Patient/{id}") {
      val id =
        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

      try {
        val patientInputId = PatientInputId(Uuid.parse(id))
        val patient =
          patientService.getPatient(patientInputId)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(fhirR4Json.encodeToString(patient), fhirContentType)
      } catch (e: Exception) {
        log.error("Feil ved henting av patient $id", e)
        call.respond(HttpStatusCode.InternalServerError, "Unable to get patient")
      }
    }
  }
}
