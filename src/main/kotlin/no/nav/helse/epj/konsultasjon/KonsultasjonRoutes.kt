package no.nav.helse.epj.konsultasjon

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.AktivKonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PatientId
import no.nav.helse.helseId.loggedInUser
import no.nav.helse.smart.valkey.ValkeyService

fun Route.konsultasjonRoutes(
  konsultasjonService: KonsultasjonService,
  valkeyService: ValkeyService,
) {

  val log = logger()
  route("/api") {
    route("/diagnoser/{id}") {
      get {
        val pasientId = call.parameters["id"] ?: error("Missing patientid")
        try {
          val pasientUuid = PatientId(Uuid.parse(pasientId))
          log.info("looking up diagnose for pasient: $pasientId")
          val diagnoser = konsultasjonService.getDiagnoser(pasientUuid)
          log.info("diagnoser for pasient: $pasientId, {}", diagnoser)
          call.respond(diagnoser)
        } catch (e: KonsultasjonNotFoundException) {
          call.respond(HttpStatusCode.BadRequest, e.message ?: "Konsultasjon feil")
        }
      }
    }
    route("/konsultasjon/{id}") {
      get {
        val konsultasjonId = call.parameters["id"] ?: error("Missing konsultasjonId")
        try {
          val konsultasjonUuid = KonsultasjonId(Uuid.parse(konsultasjonId))
          log.info("looking up konsultasjon for id: $konsultasjonId")
          val konsultasjon = konsultasjonService.getKonsultasjon(konsultasjonUuid)
          log.info("konsultasjon: $konsultasjon")
          call.respond(konsultasjon)
        } catch (e: KonsultasjonNotFoundException) {
          call.respond(HttpStatusCode.BadRequest, e.message ?: "Konsultasjon feil")
        }
      }
    }
    route("/journalnotater") {
      get("/{patientId}") {
        val patientId = call.parameters["patientId"] ?: error("Missing journalnotatId")
        try {
          val pasientUuid = PatientId(Uuid.parse(patientId))
          log.info("looking up journalnotat for patient: $patientId")
          val journalnotater =
            konsultasjonService.getJournalnotater(pasientUuid)
              ?: call.respond(HttpStatusCode.NotFound)
          log.info("journalnotat: $journalnotater")
          call.respond(journalnotater)
        } catch (e: Exception) {
          call.respond(HttpStatusCode.BadRequest, e.message ?: "journalnotat feil")
        }
      }
    }
    route("/journalnotat") {
      post {
        val request = call.receive<Journalnotat>()
        if (konsultasjonService.createJournalnotat(request)) {
          call.respond(HttpStatusCode.Created)
        } else {
          call.respond(HttpStatusCode.InternalServerError, "Kunne ikke opprette journalnotat")
        }
      }
    }
    route("/patient") { // TODO does it make sense to have consultation as a patient route?
      route("/{patientId}/konsultasjoner") {
        get {
          val pasientId = call.parameters["patientId"] ?: error("Missing  pasientId")
          try {
            val pasientUuid = PatientId(Uuid.parse(pasientId))
            log.info("looking up konsultasjoner for pasientId: $pasientId")
            val konsultasjoner = konsultasjonService.getKonsultasjoner(pasientUuid)
            log.info("konsultasjoner: $konsultasjoner")
            call.respond(konsultasjoner)
          } catch (e: KonsultasjonNotFoundException) {
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Konsultasjon feil")
          }
        }
      }
      route("/{patientId}/konsultasjon") {
        get {
          val patientId = call.parameters["patientId"] ?: error("Missing patientId")
          try {
            val patientUuidId = PatientId(Uuid.parse(patientId))
            log.info("looking up konsultasjon for patient id: $patientId")
            val konsultasjon =
              konsultasjonService.getAktivKonsultasjon(patientUuidId)
                ?: throw KonsultasjonNotFoundForPatientException(patientUuidId)
            log.info("konsultasjon: $konsultasjon")
            call.respond(konsultasjon)
          } catch (e: KonsultasjonNotFoundException) {
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Konsultasjon feil")
          }
        }
        post {
          log.info("oppretter konsutasjon")
          val pasientId =
            call.parameters["patientId"]
              ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing pasientId")

          log.info("Looking up konsultasjon for pasientId: {}", pasientId)
          val principal = loggedInUser()

          try {
            val pasientUuid = PatientId(Uuid.parse(pasientId))
            val hpr = HelsepersonellHpr(principal.hpr)
            val konsultasjon = konsultasjonService.getOrCreateKonsultasjon(pasientUuid, hpr)
            valkeyService.set(principal.hpr, pasientId)
            call.respond(konsultasjon)
          } catch (exception: Exception) {
            log.error("Kunne ikke hente eller opprette konsultasjon", exception)
            call.respond(
              HttpStatusCode.InternalServerError,
              "Konsultasjon kunne ikke hentes eller opprettes",
            )
          }
        }
        patch {
          log.info("patching konsultasjon pasientId:")
          val pasientId =
            call.parameters["patientId"]
              ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing pasientId")
          log.info("Patching konsultasjon for pasientId: {}", pasientId)
          val request = call.receive<OppdaterKonsultasjonRequest>()

          try {
            val pasientUuid = PatientId(Uuid.parse(pasientId))
            konsultasjonService.updateKonsultasjon(request, pasientUuid)
            call.respond(HttpStatusCode.OK)
          } catch (exception: KonsultasjonNotFoundException) {
            log.warn("Fant ikke konsultasjon {}", request.konsultasjonId, exception)
            call.respond(HttpStatusCode.NotFound)
          } catch (exception: UgyldigDiagnoseException) {
            log.warn("Ugyldig diagnose for konsultasjon {}", request.konsultasjonId, exception)
            call.respond(HttpStatusCode.BadRequest, exception.message ?: "Ugyldig diagnose")
          } catch (exception: Exception) {
            log.error("Kunne ikke oppdatere konsultasjon ${request.konsultasjonId}", exception)
            call.respond(HttpStatusCode.InternalServerError, "Konsultasjon ble ikke oppdatert")
          }
        }
      }
      route("/{patientId}/konsultasjon/aktiv}") {
        get {
          log.info("henter aktiv konsultasjon")
          val pasientId =
            call.parameters["patientId"]
              ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing pasientId")

          log.info("Looking up aktiv konsultasjon for pasientId: {}", pasientId)
          val principal = loggedInUser()

          try {
            val pasientUuid = PatientId(Uuid.parse(pasientId))
            val konsultasjon =
              konsultasjonService.getAktivKonsultasjon(pasientUuid)
                ?: throw AktivKonsultasjonNotFoundException(pasientUuid)
            valkeyService.set(principal.hpr, "aktiv-${pasientId}")
            call.respond(konsultasjon)
          } catch (exception: Exception) {
            log.error("Kunne ikke hente eller opprette konsultasjon", exception)
            call.respond(
              HttpStatusCode.InternalServerError,
              "Konsultasjon kunne ikke hentes eller opprettes",
            )
          }
        }
      }
    }
  }
}
