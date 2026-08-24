package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.patientInputId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

fun Route.patientRoutes(
  patientService: PatientService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  val log = logger()

  route("/fhir") {
    get("/Patient/{subject}") {
      val id = call.patientInputId()
      val principal = call.requireFhirScope("Patient", Interaction.READ)
      principal.requirePatientMatch("Patient", Interaction.READ, id.value.toString())

      val patient =
        patientService.getPatient(id) ?: return@get call.respond(HttpStatusCode.NotFound)
      call.respondText(fhirR4Json.encodeToString(patient), fhirContentType)
    }
  }
}
