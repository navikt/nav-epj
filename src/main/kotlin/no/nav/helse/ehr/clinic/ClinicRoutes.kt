package no.nav.helse.ehr.clinic

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.ehr.clinicId
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.ehr.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.clinicRoutes(clinicService: ClinicService, sessionOrgResolver: SessionOrgResolver) {
    route("api") {
        get("/clinics/{clinicId}") {
            val idParam = call.clinicId()
            val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
            val clinic = clinicService.getClinic(idParam, orgNumber)
            call.respond(clinic)
        }
    }
}
