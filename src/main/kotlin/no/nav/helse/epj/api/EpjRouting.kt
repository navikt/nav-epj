package no.nav.helse.epj.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.EpjService
import no.nav.helse.helseIdAuth.loggedInUser

// TODO: fjern denne og lagre legekontor i bd
val legekontorId = "a1000000-0000-0000-0000-000000000001"

fun Application.configureEpjRouting() {
  val epjService: EpjService by dependencies

  routing {
    authenticate("wonderwall-helseid") {
      singlePageApplication {
        useResources = true
        defaultPage = "index.html"
        filesPath = "static"
      }

      route("/api") {
        route("/helsepersonell/me") {
          get {
            val principal = loggedInUser()
            val loggedInUser = epjService.findOrCreateHelsepersonell(principal)
            call.respond(loggedInUser)
          }
        }
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
          post("/{pasientId}/konsultasjon") {
            val pasientId = call.parameters["pasientId"] ?: error("Missing  pasientId")
            log.info("looking up konsultasjon for pasientId: $pasientId")
            val principal = loggedInUser()
            val konsultasjon = epjService.getOrCreateKonsultasjon(pasientId, principal.hpr)

            call.respond(konsultasjon)
          }
        }
      }
    }
  }
}
