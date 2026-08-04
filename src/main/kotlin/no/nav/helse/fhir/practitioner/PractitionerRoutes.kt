package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger

fun Route.pracitionerRoutes(
  practitionerService: PractitionerService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Practitioner/{practitionerId}") {
      val practitionerId =
        call.parameters["practitionerId"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

      try {
        val practitioner =
          practitionerService.getPractitioner(PractitionerId(practitionerId))
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(fhirR4Json.encodeToString(practitioner), fhirContentType)
      } catch (e: Exception) {
        log.error("Error when fetching practitioner $practitionerId", e)
        call.respond(HttpStatusCode.InternalServerError, "Unable to get practitioner")
      }
    }
  }
}
