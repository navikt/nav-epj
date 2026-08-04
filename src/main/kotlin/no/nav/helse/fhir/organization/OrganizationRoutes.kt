package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.organizationRoutes(
  organizationService: OrganizationService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  route("/fhir") {
    get("/Organization/{organizationId}") {
      val organizationId =
        call.parameters["organizationId"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

      val organization = organizationService.getOrganization(OrganizationId(organizationId))
      call.respondText(fhirR4Json.encodeToString(organization), fhirContentType)
    }
  }
}
