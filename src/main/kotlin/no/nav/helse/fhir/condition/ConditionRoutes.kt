package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.patientReferenceInputId

fun Route.conditionRoutes(
  conditionService: ConditionService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  route("/fhir") {
    get("/Condition") {
      val encounterId = call.parameters["encounter"]
      val patientId = call.parameters["subject"]

      val conditions =
        when {
          patientId != null ->
            conditionService.getConditionsByPatientId(call.patientReferenceInputId())
          encounterId != null ->
            conditionService.getConditionsByEncounterId(call.encounterReferenceId())
          else -> return@get call.respond(HttpStatusCode.BadRequest)
        }
      call.respondText(fhirR4Json.encodeToString(conditions), fhirContentType)
    }
  }
}
