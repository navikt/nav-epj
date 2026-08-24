package no.nav.helse.fhir.practitionerrole

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.smart.security.Interaction

fun Route.practitionerRoleRoutes(
  practitionerRoleService: PractitionerRoleService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  route("/fhir") {
    get("/PractitionerRole") {
      val practitionerRef =
        call.parameters["practitioner"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "missing practitioner parameter")
      call.requireFhirScope("PractitionerRole", Interaction.SEARCH)

      val hpr = practitionerRef.substringAfterLast('/')
      val bundle = practitionerRoleService.getPractitionerRolesByPractitioner(hpr)
      call.respondText(fhirR4Json.encodeToString(bundle), fhirContentType)
    }
  }
}
