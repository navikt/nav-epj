package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.patientInputId

fun Route.patientRoutes(
  patientService: PatientService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  val log = logger()

  route("/fhir") {
    get("/Patient/{subject}") {
      val id = call.patientInputId()
      val patient =
        patientService.getPatient(id) ?: return@get call.respond(HttpStatusCode.NotFound)
      call.respondText(fhirR4Json.encodeToString(patient), fhirContentType)
    }
  }
}
