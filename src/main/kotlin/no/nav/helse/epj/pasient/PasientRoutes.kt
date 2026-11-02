package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.securelog
import no.nav.helse.epj.patientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

private val securelog = securelog()

fun Route.pasientRoutes(pasientService: PasientService, sessionOrgResolver: SessionOrgResolver) {

    route("/api") {
        route("/patient") {
            get {
                val principal = loggedInUser()
                val hpr = PractitionerHpr(principal.hpr)
                val orgNumber = sessionOrgResolver.requireOrgNumber(principal.hpr)
                call.respond(pasientService.listAtClinic(orgNumber, hpr))
            }
            post {
                val principal = loggedInUser()
                val request = call.receive<OpprettPasientRequest>()
                val orgNumber = sessionOrgResolver.requireOrgNumber(principal.hpr)
                val pasient = pasientService.createPasient(request, principal.hpr, orgNumber)
                call.respond(HttpStatusCode.Created, pasient)
            }
            get("/{patientId}") {
                val id = call.patientId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val pasient =
                    pasientService.getPasientById(id)?.takeIf {
                        pasientService.isListedAt(id, orgNumber) ||
                            pasientService.hasKonsultasjonAt(id, orgNumber)
                    } ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
                call.respond(pasient)
            }
        }
    }
}
