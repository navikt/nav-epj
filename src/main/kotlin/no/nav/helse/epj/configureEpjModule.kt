package no.nav.helse.epj

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureEpjModule() {
  configureEpjDependencies()
  val epjService: EpjService by dependencies

  routing {
    singlePageApplication {
      useResources = true
      defaultPage = "index.html"
      filesPath = "static"
    }

    route("api") {
      get("/patient") {
        val pasienter = epjService.getPasienter()
        print("hello ${pasienter.first()}!")
        call.respond(pasienter)
      }
      get("/patient/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        call.respond(epjService.getPasient(id))
      }
    }
  }
}
