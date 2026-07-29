package no.nav.helse.fhir.Patient

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.smart.security.SmartPrincipal

@OptIn(ExperimentalUuidApi::class)
fun Route.patientRoutes(
  patientService: PatientService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  val log = logger()

  route("/fhir") {
    get("/Patient/{id}") {
      val principal = call.principal<SmartPrincipal>()!!
      val authorizedPatient =
        principal.patient
          ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")

      val id = call.parameters["id"]!!
      if (id != authorizedPatient) {
        return@get call.respond(HttpStatusCode.NotFound)
      }
      try {
        val patientInputId = PatientInputId(Uuid.parse(id))
        val patient =
          patientService.getPatient(patientInputId)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(fhirR4Json.encodeToString(patient), fhirContentType)
      } catch (e: Exception) {
        log.error("Feil ved henting av patient $id", e)
      }
    }
  }
}
