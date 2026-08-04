package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger

fun Route.organizationRoutes(
  organizationService: OrganizationService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Organization/{organizationId}") {
      val organizationId =
        call.parameters["organizationId"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

      try {
        val organization =
          organizationService.getOrganization(OrganizationId(organizationId))
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(fhirR4Json.encodeToString(organization), fhirContentType)
      } catch (e: Exception) {
        log.error("Error when fetching organization $organizationId", e)
        call.respond(HttpStatusCode.BadRequest, "Unable to fetch organization")
      }
    }
  }
}
