package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.patientInputId

fun Route.conditionRoutes(
  conditionService: ConditionService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()

  route("/fhir") {
    get("/Condition") {
      val patient = call.patientInputId()
      val conditions = conditionService.getConditions(patient)
      val bundle =
        Bundle(
          type = Enumeration(value = Bundle.BundleType.Searchset),
          entry =
            conditions.map { condition ->
              Bundle.Entry(fullUrl = Uri(value = "Condition/${condition.id}"), resource = condition)
            },
        )
      call.respondText(fhirR4Json.encodeToString(bundle), fhirContentType)
    }
  }
}
