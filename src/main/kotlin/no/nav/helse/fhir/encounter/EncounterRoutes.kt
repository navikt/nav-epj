package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.encounterId

fun Route.encounterRoutes(
  encounterService: EncounterService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Encounter/{encounter}") {
      val id = call.encounterId()
      val encounter =
        encounterService.getEncounterById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
      call.respondText(fhirR4Json.encodeToString(encounter), fhirContentType)
    }
  }
}
