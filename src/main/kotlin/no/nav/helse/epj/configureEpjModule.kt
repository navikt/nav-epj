package no.nav.helse.epj

import io.ktor.server.application.*
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val mockPatient =
  Patient(id = "pasient-1", name = "Test testersen", birthDate = "1980-05-15")

fun Application.configureEpjModule() {
  routing {
    singlePageApplication {
      useResources = true
      defaultPage = "index.html"
      filesPath = "static"
    }

    route("api") {
      get("/patient/{id}") { call.respond(mockPatient) }
      get("/patient") { call.respond(listOf(mockPatient)) }
    }
  }
}
