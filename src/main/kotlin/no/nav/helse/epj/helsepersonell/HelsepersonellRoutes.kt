package no.nav.helse.epj.helsepersonell

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.core.utils.logger
import no.nav.helse.helseId.loggedInUser

fun Route.helsepersonellRoutes(helsepersonellService: HelsepersonellService) {
  val log = logger()

  route("/api") {
    get("/helsepersonell/me") {
      val principal = loggedInUser()
      try {
        log.error("==========================")
        log.error("$principal")
        log.error("==========================")
        val hpr = HelsepersonellHpr(principal.hpr)
        val loggedInUser = helsepersonellService.findOrCreateHelsepersonell(hpr, principal.name)
        call.respond(loggedInUser)
      } catch (e: HelsepersonellNotFoundException) {
        call.respond(HttpStatusCode.NotFound, "Helsepersonell not found: ${e.message}")
      } catch (e: Exception) {
        call.respond(
          HttpStatusCode.InternalServerError,
          "An error occured when getting helsepersonell: ${e.message}",
        )
      }
    }

    get("/helsepersonell/{hpr}") {
      val hpr =
        call.parameters["hpr"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hpr")

      try {
        val hprId = HelsepersonellHpr(hpr)
        val helsepersonell = helsepersonellService.getHelsepersonell(hprId)
        call.respond(HttpStatusCode.OK, helsepersonell)
      } catch (e: HelsepersonellNotFoundException) {
        call.respond(HttpStatusCode.NotFound, "Helsepersonell not found: ${e.message}")
      } catch (e: Exception) {
        call.respond(
          HttpStatusCode.InternalServerError,
          "An error occured when getting helsepersonell: ${e.message}",
        )
      }
    }
  }
}
