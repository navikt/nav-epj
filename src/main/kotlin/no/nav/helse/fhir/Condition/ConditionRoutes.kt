package no.nav.helse.fhir.Condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.Encounter.EncounterId
import no.nav.helse.fhir.Patient.PatientInputId
import no.nav.helse.smart.security.SmartPrincipal

@OptIn(ExperimentalUuidApi::class)
fun Route.conditionRoutes(
  conditionService: ConditionService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()

  route("/fhir") {
    get("/Condition") {
      val principal = call.principal<SmartPrincipal>()!!
      val encounter =
        principal.encounter
          ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no encounter context")

      val patient =
        principal.patient
          ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")

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
