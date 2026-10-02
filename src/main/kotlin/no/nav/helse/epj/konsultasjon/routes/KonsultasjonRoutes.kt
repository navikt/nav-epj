package no.nav.helse.epj.konsultasjon.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.OppdaterKonsultasjonRequest
import no.nav.helse.epj.konsultasjonId
import no.nav.helse.epj.pasient.ActivePatientService
import no.nav.helse.epj.patientId
import no.nav.helse.helseId.loggedInUser

fun Route.konsultasjonRoutes(
    konsultasjonService: KonsultasjonService,
    activePatientService: ActivePatientService,
) {

    val log = logger()
    route("/api") {
        route("/patients/{patientId}/konsultasjoner") {
            get {
                val pasientId = call.patientId()
                call.respond(konsultasjonService.getKonsultasjoner(pasientId))
            }
            post {
                val pasientId = call.patientId()
                val principal = loggedInUser()
                val hpr = HelsepersonellHpr(principal.hpr)
                activePatientService.claimActivePatient(principal.hpr, pasientId.value)
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                call.respond(konsultasjonService.getOrCreateKonsultasjon(pasientId, hpr))
            }
            patch {
                val request = call.receive<OppdaterKonsultasjonRequest>()
                val pasientId = call.patientId()
                konsultasjonService.updateKonsultasjon(request, pasientId)
                call.respond(HttpStatusCode.OK)
            }
            post("/{konsultasjonId}/avbryt") {
                val pasientId = call.patientId()
                val konsultasjonId = call.konsultasjonId()
                konsultasjonService.cancelKonsultasjon(konsultasjonId, pasientId)
                call.respond(HttpStatusCode.OK)
            }
            get("/active") {
                val pasientId = call.patientId()
                val konsultasjoner =
                    konsultasjonService.getAktivKonsultasjon(pasientId)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(konsultasjoner)
            }
        }
        route("/konsultasjon/{konsultasjonId}") {
            get {
                val konsultasjonId = call.konsultasjonId()
                val konsultasjon = konsultasjonService.getKonsultasjon(konsultasjonId)
                call.respond(konsultasjon)
            }
        }
    }
}
