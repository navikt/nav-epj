package no.nav.helse.epj.konsultasjon.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.OppdaterKonsultasjonRequest
import no.nav.helse.epj.konsultasjonId
import no.nav.helse.epj.patient.ActivePatientService
import no.nav.helse.epj.patientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.konsultasjonRoutes(
    konsultasjonService: KonsultasjonService,
    activePatientService: ActivePatientService,
    sessionOrgResolver: SessionOrgResolver,
) {

    val log = logger()
    route("/api") {
        route("/patients/{patientId}/konsultasjoner") {
            get {
                val patientId = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                call.respond(konsultasjonService.getKonsultasjoner(patientId, orgNumber))
            }
            post {
                val patientId = call.patientId()
                val principal = loggedInUser()
                val hpr = PractitionerHpr(principal.hpr)
                activePatientService.claimActivePatient(principal.hpr, patientId.value)
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                val orgNumber = sessionOrgResolver.requireOrgNumber(principal.hpr)
                call.respond(konsultasjonService.getOrCreateKonsultasjon(patientId, hpr, orgNumber))
            }
            patch {
                val request = call.receive<OppdaterKonsultasjonRequest>()
                val patientId = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                konsultasjonService.updateKonsultasjon(request, patientId, orgNumber)
                call.respond(HttpStatusCode.OK)
            }
            post("/{konsultasjonId}/avbryt") {
                val patientId = call.patientId()
                val konsultasjonId = call.konsultasjonId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                konsultasjonService.cancelKonsultasjon(konsultasjonId, patientId, orgNumber)
                call.respond(HttpStatusCode.OK)
            }
            get("/active") {
                val patientId = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val konsultasjoner =
                    konsultasjonService.getAktivKonsultasjon(patientId, orgNumber)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(konsultasjoner)
            }
        }
        route("/konsultasjon/{konsultasjonId}") {
            get {
                val konsultasjonId = call.konsultasjonId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val konsultasjon = konsultasjonService.getKonsultasjon(konsultasjonId, orgNumber)
                call.respond(konsultasjon)
            }
        }
    }
}
