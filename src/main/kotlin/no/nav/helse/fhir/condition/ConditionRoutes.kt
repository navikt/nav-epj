package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.encounterId
import no.nav.helse.fhir.patientInputId

fun Route.conditionRoutes(
  conditionService: ConditionService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()

  route("/fhir") {
    get("/Condition") {
      val encounterId = call.parameters["encounter"]
      val patientId = call.parameters["subject"]

      if (patientId != null) {
        val id = call.patientInputId()
        val conditions = conditionService.getConditionsByPatientId(id)
        call.respondText(fhirR4Json.encodeToString(conditions), fhirContentType)
      }
      if (encounterId != null) {
        val id = call.encounterId()
        val conditions = conditionService.getConditionsByEncounterId(id)
        call.respondText(fhirR4Json.encodeToString(conditions), fhirContentType)
      }
      call.respond(HttpStatusCode.NotFound)
    }
  }
}
