package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.securelog
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.patientId
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgnummer
import no.nav.helse.helseId.loggedInUser

private val securelog = securelog()

fun Route.pasientRoutes(pasientService: PasientService, sessionOrgResolver: SessionOrgResolver) {

    route("/api") {
        route("/patient") {
            get {
                val principal = loggedInUser()
                val hpr = HelsepersonellHpr(principal.hpr)
                val orgnummer = sessionOrgResolver.requireOrgnummer(principal.hpr)
                call.respond(pasientService.listAtLegekontor(orgnummer, hpr))
            }
            post {
                val principal = loggedInUser()
                val request = call.receive<OpprettPasientRequest>()
                val orgnummer = sessionOrgResolver.requireOrgnummer(principal.hpr)
                val pasient = pasientService.createPasient(request, principal.hpr, orgnummer)
                call.respond(HttpStatusCode.Created, pasient)
            }
            get("/{patientId}") {
                val id = call.patientId()
                val pasient =
                    pasientService.getPasientById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
                call.respond(pasient)
            }
        }
    }
}
