package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Uri
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.practitioner.repository.PractitionerRepo
import no.nav.helse.fhir.respondFhir

fun Route.configurePractitionerRouting() {
  val practitionerService = PractitionerService(PractitionerRepo())
  get("/Practitioner/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }

    val id =
      call.parameters["id"]
        ?: return@get call.respondText(
          "Missing practitioner id",
          status = HttpStatusCode.BadRequest,
        )

    val practitioner = practitionerService.getPractitioner(id)
    if (practitioner != null) {
      call.respondFhir(practitioner)
    } else {
      call.respondText("Practitioner not found", status = HttpStatusCode.NotFound)
    }
  }

  get("/Practitioner") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }

    val practitioners = practitionerService.getAllPractitioners()
    val bundle =
      Bundle(
        type = Enumeration(value = Bundle.BundleType.Searchset),
        entry =
          practitioners.map { practitioner ->
            Bundle.Entry(
              fullUrl = Uri(value = "Practitioner/${practitioner.id}"),
              resource = practitioner,
            )
          },
      )
    call.respondFhir(bundle)
  }

  post("/Practitioner") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@post
    }
    val body = call.receiveText()
    val practitioner = fhirJson.decodeFromString(body) as Practitioner
    val created = practitionerService.createPractitioner(practitioner)
    call.respondFhir(created)
  }
}
