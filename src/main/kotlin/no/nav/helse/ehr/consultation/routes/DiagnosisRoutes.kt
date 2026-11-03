package no.nav.helse.ehr.consultation.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.consultationId
import no.nav.helse.ehr.patientId
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.ehr.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.diagnosisRoutes(
    consultationService: ConsultationService,
    sessionOrgResolver: SessionOrgResolver,
) {

    route("/api") {
        route("/diagnoses") {
            get {
                val patientId = call.parameters["patientId"]
                val consultationId = call.parameters["consultationId"]
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)

                val diagnoses =
                    when {
                        patientId != null ->
                            consultationService.getDiagnoses(call.patientId(), orgNumber)
                        consultationId != null ->
                            consultationService.getDiagnoses(call.consultationId(), orgNumber)
                        else -> emptyList()
                    }
                call.respond(diagnoses)
            }
        }
    }
}
