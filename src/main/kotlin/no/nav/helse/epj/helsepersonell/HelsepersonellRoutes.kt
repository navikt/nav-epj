package no.nav.helse.epj.helsepersonell

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PatientId
import no.nav.helse.helseId.loggedInUser

fun Route.helsepersonellRoutes(helsepersonellService: HelsepersonellService) {
  route("/api") {
    route("/helsepersonell") {
      get("/me") {
        val principal = loggedInUser()
        try {
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
      get("/{hpr}") {
        val hpr =
          call.parameters["hpr"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hpr")

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

      get("/konsultasjon/{konsultasjonId}") {
        val konsultasjonId =
          call.parameters["konsultasjonId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hpr")

        try {
          val konsultasjonId = KonsultasjonId(Uuid.parse(konsultasjonId))
          val helsepersonell = helsepersonellService.getHelsepersonell(konsultasjonId)
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

      get("/patient/{patientId}") {
        // returnerer liste av helsepersonell på pasient
        val patientId =
          call.parameters["patientId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing hpr")

        try {
          val patientUuid = PatientId(Uuid.parse(patientId))
          val helsepersonell = helsepersonellService.getHelsepersonell(patientUuid)
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
}
