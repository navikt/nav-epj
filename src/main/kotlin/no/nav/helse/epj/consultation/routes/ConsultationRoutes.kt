package no.nav.helse.epj.consultation.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.consultation.ConsultationService
import no.nav.helse.epj.consultation.UpdateConsultationRequest
import no.nav.helse.epj.consultationId
import no.nav.helse.epj.patient.ActivePatientService
import no.nav.helse.epj.patientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.consultationRoutes(
    consultationService: ConsultationService,
    activePatientService: ActivePatientService,
    sessionOrgResolver: SessionOrgResolver,
) {

    val log = logger()
    route("/api") {
        route("/patients/{patientId}/consultations") {
            get {
                val patientId = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                call.respond(consultationService.getConsultations(patientId, orgNumber))
            }
            post {
                val patientId = call.patientId()
                val principal = loggedInUser()
                val hpr = PractitionerHpr(principal.hpr)
                activePatientService.claimActivePatient(principal.hpr, patientId.value)
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                val orgNumber = sessionOrgResolver.requireOrgNumber(principal.hpr)
                call.respond(consultationService.getOrCreateConsultation(patientId, hpr, orgNumber))
            }
            patch {
                val request = call.receive<UpdateConsultationRequest>()
                val patientId = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                consultationService.updateConsultation(request, patientId, orgNumber)
                call.respond(HttpStatusCode.OK)
            }
            post("/{consultationId}/cancel") {
                val patientId = call.patientId()
                val consultationId = call.consultationId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                consultationService.cancelConsultation(consultationId, patientId, orgNumber)
                call.respond(HttpStatusCode.OK)
            }
            get("/active") {
                val patientId = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val consultations =
                    consultationService.getActiveConsultation(patientId, orgNumber)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(consultations)
            }
        }
        route("/consultations/{consultationId}") {
            get {
                val consultationId = call.consultationId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val consultation = consultationService.getConsultation(consultationId, orgNumber)
                call.respond(consultation)
            }
        }
    }
}
