package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.practitionerId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.smart.security.Interaction

fun Route.pracitionerRoutes(
  practitionerService: PractitionerService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/Practitioner/{practitionerId}") {
      call.requireFhirScope("Practitioner", Interaction.READ)
      val practitionerId = call.practitionerId()

      val practitioner =
        practitionerService.getPractitioner(practitionerId)
          ?: return@get call.respond(HttpStatusCode.NotFound)
      call.respondText(fhirR4Json.encodeToString(practitioner), fhirContentType)
    }
  }
}
