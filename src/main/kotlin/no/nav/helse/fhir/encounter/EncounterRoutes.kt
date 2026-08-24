package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.encounterId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

fun Route.encounterRoutes(
  encounterService: EncounterService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Encounter/{encounter}") {
      val id = call.encounterId()
      val principal = call.requireFhirScope("Encounter", Interaction.READ)

      val encounter =
        encounterService.getEncounterById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
      principal.requirePatientMatch(
        "Encounter",
        Interaction.READ,
        encounter.subject?.reference?.value?.substringAfter("Patient/"),
      )

      val fhirJson = fhirR4Json.encodeToString(encounter)
      log.info("encounter: $fhirJson")
      call.respondText(fhirJson, fhirContentType)
    }
  }
}
