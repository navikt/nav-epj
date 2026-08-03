package no.nav.helse.fhir.Encounter

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.smart.security.SmartPrincipal

@OptIn(ExperimentalUuidApi::class)
fun Route.encounterRoutes(
  encounterService: EncounterService,
  fhirjson: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Encounter/{encounterId}") {
      val id =
        call.parameters["encounterId"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
      val principal = call.principal<SmartPrincipal>()!!
      val authorizedPatient =
        principal.patient
          ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")

      //TODO: ?
      val patientParam =
        call.request.queryParameters["patient"]
          ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "Missing 'patient' search parameter",
          )

      if (patientParam != authorizedPatient) {
        return@get call.respond(
          HttpStatusCode.Forbidden,
          "Not permitted to search outside the patient context",
        )
      }

      try {
        val encounterId = EncounterId(Uuid.parse(id))
        val bundle = encounterService.getEncounterById(encounterId)
        call.respondText(fhirjson.encodeToString(bundle), fhirContentType)
      } catch (e: Exception) {
        log.error("Encounter returned error while fetching Encounters", e)
      }
    }
  }
}
