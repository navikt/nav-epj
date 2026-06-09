package no.nav.helse.epj

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

    route("api") { get("/patient") { call.respond(epjService.getPasienter()) } }
  }
}
