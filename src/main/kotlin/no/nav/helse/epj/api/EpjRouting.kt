package no.nav.helse.epj.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.ClinicianContext
import no.nav.helse.epj.ClinicianContextStore
import no.nav.helse.epj.EpjService
import no.nav.helse.helseIdAuth.loggedInUser

// TODO fjern denne og lagre legekontor i db
const val LEGEKONTOR_ID = "a1000000-0000-0000-0000-000000000001"

fun Application.configureEpjRouting() {
  val epjService: EpjService by dependencies
  val clinicianContextStore: ClinicianContextStore by dependencies

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
            val principal = loggedInUser()
            val pasient = epjService.getPasienterForInnloggetLege(principal.hpr)
            call.respond(pasient)
          }
          post {
            val principal = loggedInUser()
            val request = call.receive<OpprettPasientRequest>()
            runCatching { epjService.opprettPasient(request, principal.hpr) }
              .onSuccess { pasient -> call.respond(HttpStatusCode.Created, pasient) }
              .onFailure { exception ->
                log.error("Kunne ikke opprette pasient", exception)
                call.respond(HttpStatusCode.InternalServerError, "Pasient ble ikke opprettet")
              }
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

          route("/{pasientId}/konsultasjoner") {
            get {
              val pasientId = call.parameters["pasientId"] ?: error("Missing  pasientId")
              log.info("looking up konsultasjoner for pasientId: $pasientId")
              val konsultasjoner = epjService.getKonsultasjoner(pasientId)
              log.info("konsultasjoner: $konsultasjoner")
              call.respond(konsultasjoner)
            }
          }

          post("/{pasientId}/konsultasjon") {
            val pasientId = call.parameters["pasientId"] ?: error("Missing  pasientId")
            log.info("looking up konsultasjon for pasientId: $pasientId")
            val principal = loggedInUser()
            runCatching { epjService.getOrCreateKonsultasjon(pasientId, principal.hpr) }
              .onSuccess { konsultasjon ->
                /**
                 * TODO Context is keyed by hpr, so one clinician with two patients open in separate
                 * tabs shares a single context and the second overwrites the first. Key by a
                 * per-tab session id instead once we have session handling.
                 */
                clinicianContextStore.set(principal.hpr, ClinicianContext(pasientId))
                call.respond(konsultasjon)
              }
              .onFailure { exception ->
                log.error("Kunne ikke hente eller opprette konsultasjon", exception)
                call.respond(
                  HttpStatusCode.InternalServerError,
                  "Konsultasjon kunne ikke hentes eller opprettes",
                )
              }
          }
          patch("/{pasientId}/konsultasjon") {
            log.info("patching konsultasjon pasientId:")
            val pasientId = call.parameters["pasientId"] ?: error("Missing  pasientId")
            val request = call.receive<OppdaterKonsultasjonRequest>()

            runCatching { epjService.oppdaterKonsultasjon(request, pasientId) }
              .onSuccess { call.respond(HttpStatusCode.OK) }
              .onFailure { exception ->
                log.error("Kunne ikke oppdatere konsultasjon ${request.konsultasjonId}", exception)
                call.respond(HttpStatusCode.InternalServerError, "Konsultasjon ble ikke oppdatert")
              }
          }
        }
      }
    }
  }
}
