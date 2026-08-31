package no.nav.helse.epj.helsepersonell

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.helsepersonellHpr
import no.nav.helse.epj.konsultasjonId
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorService
import no.nav.helse.epj.patientId
import no.nav.helse.helseId.loggedInUser

fun Route.helsepersonellRoutes(
  helsepersonellService: HelsepersonellService,
  legekontorService: LegekontorService,
) {

  route("/api") {
    route("/helsepersonell") {
      get("/me") {
        val principal = loggedInUser()
        // TODO: connect legekontor to logged in user
        legekontorService.insertIfNotExists(Legekontor.DEFAULT.id.value)
        val hpr = HelsepersonellHpr(principal.hpr)
        val loggedInUser = helsepersonellService.findOrCreateHelsepersonell(hpr, principal.name)
        call.respond(loggedInUser)
      }
      get("/{hpr}") {
        val hpr = call.helsepersonellHpr()
        val helsepersonell = helsepersonellService.getHelsepersonell(hpr)
        call.respond(HttpStatusCode.OK, helsepersonell)
      }

      get("/konsultasjon/{konsultasjonId}") {
        val konsultasjonId = call.konsultasjonId()
        val helsepersonell = helsepersonellService.getHelsepersonell(konsultasjonId)
        call.respond(HttpStatusCode.OK, helsepersonell)
      }

      get("/patient/{patientId}") {
        val patientId = call.patientId()
        val helsepersonell = helsepersonellService.getHelsepersonell(patientId)
        call.respond(HttpStatusCode.OK, helsepersonell)
      }
    }
  }
}
