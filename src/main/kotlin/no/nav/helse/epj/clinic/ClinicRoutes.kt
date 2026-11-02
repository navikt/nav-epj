package no.nav.helse.epj.clinic

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.clinicId
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgNumber
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
