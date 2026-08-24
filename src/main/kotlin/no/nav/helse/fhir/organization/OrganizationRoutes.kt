package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.organizationId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.smart.security.Interaction

fun Route.organizationRoutes(
  organizationService: OrganizationService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Organization/{organizationId}") {
      call.requireFhirScope("Organization", Interaction.READ)
      val organizationId = call.organizationId()
      val organization =
        organizationService.getOrganization(organizationId)
          ?: return@get call.respond(HttpStatusCode.NotFound)
      call.respondText(fhirR4Json.encodeToString(organization), fhirContentType)
    }
  }
}
