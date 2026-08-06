package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.patientId
import no.nav.helse.helseId.loggedInUser

fun Route.pasientRoutes(pasientService: PasientService) {
  val log = logger()

  route("/api") {
    route("/patient") {
      get {
        val principal = loggedInUser()
        val hpr = HelsepersonellHpr(principal.hpr)
        val pasient = pasientService.getPasienterByHpr(hpr)
        call.respond(pasient)
      }
      post {
        val principal = loggedInUser()
        val request = call.receive<OpprettPasientRequest>()
        val pasient = pasientService.createPasient(request, principal.hpr)
        call.respond(HttpStatusCode.Created, pasient)
      }
      get("/{patientId}") {
        val id = call.patientId()
        val pasient =
          pasientService.getPasientById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
        call.respond(pasient)
      }
    }
  }
}
