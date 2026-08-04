package no.nav.helse.epj.helsepersonell

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.helseId.loggedInUser

fun Route.helsepersonellRoutes(helsepersonellService: HelsepersonellService) {
  route("/api") {
    route("/helsepersonell/me") {
      get {
        val principal = loggedInUser()
        try {
          val hpr = HelsepersonellHpr(principal.hpr)
          val loggedInUser = helsepersonellService.findOrCreateHelsepersonell(hpr, principal.name)
          call.respond(loggedInUser)
        } catch (e: HelsepersonellNotFoundException) {
          call.respond(HttpStatusCode.NotFound, "Helsepersonell not found: ${e.message}")
        } catch (e: Exception) {
          call.respond(HttpStatusCode.InternalServerError, "Helsepersonell not found: ${e.message}")
        }
      }
    }
  }
}
