package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.respondFhir

fun Route.configureOrganizationRouting() {
  val organizationService: OrganizationService by application.dependencies
  get("/Organization/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val id =
      call.parameters["id"]
        ?: return@get call.respondText(
          "Missing organization id",
          status = HttpStatusCode.BadRequest,
        )
    val organization = organizationService.getOrganization(id)
    if (organization != null) {
      call.respondFhir(organization)
    } else {
      call.respondText("Organization not found", status = HttpStatusCode.NotFound)
    }
  }

  get("/Organization") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val organizations = organizationService.getAllOrganizations()
    val bundle =
      Bundle(
        type = Enumeration(value = Bundle.BundleType.Searchset),
        entry =
          organizations.map { organization ->
            Bundle.Entry(
              fullUrl = Uri(value = "Organization/${organization.id}"),
              resource = organization,
            )
          },
      )
    call.respondFhir(bundle)
  }

  post("/Organization") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@post
    }
    val body = call.receiveText()
    val organization = fhirJson.decodeFromString(body) as Organization
    val created = organizationService.createOrganization(organization)
    call.respondFhir(created)
  }
}
