package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

@OptIn(ExperimentalUuidApi::class)
fun Route.conditionRoutes(
  conditionService: ConditionService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()

  route("/fhir") {
    get("/Condition") {
      val encounter =
        call.request.queryParameters["encounter"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing encounter search param")
      val patient =
        call.request.queryParameters["patient"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing patient search param")

      try {
        val patientId = PatientInputId(Uuid.parse(patient))
        val encounterId = EncounterId(Uuid.parse(encounter))
        val conditions = conditionService.getConditions(encounterId, patientId)

        val bundle =
          Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
              conditions.map { condition ->
                Bundle.Entry(
                  fullUrl = Uri(value = "Condition/${condition.id}"),
                  resource = condition,
                )
              },
          )
        call.respondText(fhirR4Json.encodeToString(bundle), fhirContentType)
      } catch (e: Exception) {
        log.error("Feil ved henting av conditions", e)
      }
    }
  }
}
