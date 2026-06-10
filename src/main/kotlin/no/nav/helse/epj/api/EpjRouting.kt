package no.nav.helse.epj.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.EpjService

fun Application.configureEpjRouting() {
  val epjService: EpjService by dependencies

  routing {
    singlePageApplication {
      useResources = true
      defaultPage = "index.html"
      filesPath = "static"
    }

    route("api") {
      route("/patient") {
        get {
          val pasient = epjService.getPasienter()
          call.respond(pasient)
        }
        get("/{id}") {
          val id =
            call.parameters["id"]
              ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing pasient id")
          val pasient =
            epjService.getPasient(id)
              ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
          call.respond(pasient)
        }
        post { TODO("not implemented yet") }
      }
    }
  }
}
