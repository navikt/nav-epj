package no.nav.helse.fhir.Organization

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.organizationRoutes(
  organizationService: OrganizationService,
  fhirR4Json: FhirR4Json,
  fhirContentType: ContentType,
) {

  route("/fhir") {
    get("/Organization") {
      val organization = organizationService.getOrganization()
      val bundle =
        Bundle(
          type = Enumeration(value = Bundle.BundleType.Searchset),
          entry =
            listOf(Bundle.Entry(fullUrl = Uri(value = "Organization"), resource = organization)),
        )
      call.respondText(fhirR4Json.encodeToString(bundle), fhirContentType)
    }
  }
}
