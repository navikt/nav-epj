package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.helseId.loggedInUser

fun Route.pasientRoutes(pasientService: PasientService) {
  val log = logger()

  route("/api") {
    route("/patient") {
      get {
        val principal = loggedInUser()
        try {
          val hpr = HelsepersonellHpr(principal.hpr)
          val pasient = pasientService.getPasienterByHpr(hpr)
          call.respond(pasient)
        } catch (e: Exception) {
          call.respond(HttpStatusCode.BadRequest, e.message ?: "Something went wrong")
        }
      }
      post {
        val principal = loggedInUser()
        val request = call.receive<OpprettPasientRequest>()
        try {
          val pasient = pasientService.createPasient(request, principal.hpr)
          call.respond(HttpStatusCode.Created, pasient)
        } catch (exception: Exception) {
          log.error("Kunne ikke opprette pasient", exception)
          call.respond(HttpStatusCode.InternalServerError, "Pasient ble ikke opprettet")
        }
      }
      get("/{id}") {
        val id =
          call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing pasient id")

        try {
          val pasientId = PatientId(Uuid.parse(id))
          val pasient =
            pasientService.getPasientById(pasientId)
              ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
          call.respond(pasient)
        } catch (e: Exception) {
          log.error("Kunne ikke hente pasient", e)
        }
      }
    }
  }
}
