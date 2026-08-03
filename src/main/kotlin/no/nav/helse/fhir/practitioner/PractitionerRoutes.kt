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
    get("/Practitioner/{id}") {
      try {
        val practitioner = practitionerService.getPractitioner()
        call.respondText(fhirR4Json.encodeToString(practitioner), fhirContentType)
      } catch (e: Exception) {
        log.error("Feil ved henting av practitioner", e)
      }
    }
  }
}
