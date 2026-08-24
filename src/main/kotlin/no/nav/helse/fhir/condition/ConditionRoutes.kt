package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.patientReferenceInputId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

fun Route.conditionRoutes(
  conditionService: ConditionService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  route("/fhir") {
    get("/Condition") {
      val encounterId = call.parameters["encounter"]
      val patientId = call.parameters["subject"]
      val principal = call.requireFhirScope("Condition", Interaction.SEARCH)

      val conditions =
        when {
          patientId != null -> {
            val id = call.patientReferenceInputId()
            principal.requirePatientMatch("Condition", Interaction.SEARCH, id.value.toString())
            conditionService.getConditionsByPatientId(id)
          }
          encounterId != null -> {
            val bundle = conditionService.getConditionsByEncounterId(call.encounterReferenceId())
            bundle.entry.forEach { entry ->
              val subject = (entry.resource as? Condition)?.subject?.reference?.value
              principal.requirePatientMatch(
                "Condition",
                Interaction.SEARCH,
                subject?.substringAfter("Patient/"),
              )
            }
            bundle
          }
          else -> return@get call.respond(HttpStatusCode.BadRequest)
        }
      call.respondText(fhirR4Json.encodeToString(conditions), fhirContentType)
    }
  }
}
