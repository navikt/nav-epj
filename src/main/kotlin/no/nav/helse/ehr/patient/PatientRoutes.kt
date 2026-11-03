package no.nav.helse.ehr.patient

import io.ktor.http.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.securelog
import no.nav.helse.ehr.patientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.ehr.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

private val securelog = securelog()

fun Route.patientRoutes(patientService: PatientService, sessionOrgResolver: SessionOrgResolver) {

    route("/api") {
        route("/patient") {
            get {
                val principal = loggedInUser()
                val hpr = PractitionerHpr(principal.hpr)
                val orgNumber = sessionOrgResolver.requireOrgNumber(principal.hpr)
                call.respond(patientService.listAtClinic(orgNumber, hpr))
            }
            post {
                val principal = loggedInUser()
                val request = call.receive<CreatePatientRequest>()
                val orgNumber = sessionOrgResolver.requireOrgNumber(principal.hpr)
                val patient = patientService.createPatient(request, principal.hpr, orgNumber)
                call.respond(HttpStatusCode.Created, patient)
            }
            get("/{patientId}") {
                val id = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val patient =
                    patientService.getPatientById(id)?.takeIf {
                        patientService.isListedAt(id, orgNumber) ||
                            patientService.hasConsultationAt(id, orgNumber)
                    } ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
                call.respond(patient)
            }
        }
    }
}
