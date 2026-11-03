package no.nav.helse.ehr.practitioner

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.ehr.practitionerHpr
import no.nav.helse.helseId.loggedInUser

fun Route.practitionerRoutes(practitionerService: PractitionerService) {

    route("/api") {
        route("/practitioners") {
            get("/me") {
                val principal = loggedInUser()
                val hpr = PractitionerHpr(principal.hpr)
                val loggedInUser = practitionerService.findOrCreatePractitioner(hpr, principal.name)
                call.respond(loggedInUser)
            }
            get("/{hpr}") {
                val hpr = call.practitionerHpr()
                val practitioner = practitionerService.getPractitioner(hpr)
                call.respond(HttpStatusCode.OK, practitioner)
            }
        }
    }
}
