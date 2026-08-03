package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger

@OptIn(ExperimentalUuidApi::class)
fun Route.encounterRoutes(
  encounterService: EncounterService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Encounter/{encounterId}") {
      val id =
        call.parameters["encounterId"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

      try {
        val encounterId = EncounterId(Uuid.parse(id))
        val encounter =
          encounterService.getEncounterById(encounterId)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(fhirR4Json.encodeToString(encounter), fhirContentType)
      } catch (e: Exception) {
        log.error("Encounter returned error while fetching Encounters", e)
        call.respond(HttpStatusCode.InternalServerError, "Unable to get encounter")
      }
    }
  }
}
