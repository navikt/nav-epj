package no.nav.helse.epj.konsultasjon.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjonId
import no.nav.helse.epj.patientId
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgnummer
import no.nav.helse.helseId.loggedInUser

fun Route.diagnoseRoutes(
    konsultasjonService: KonsultasjonService,
    sessionOrgResolver: SessionOrgResolver,
) {

    route("/api") {
        route("/diagnoser") {
            get {
                val pasientId = call.parameters["patientId"]
                val konsultasjonId = call.parameters["konsultasjonId"]
                val orgnummer = sessionOrgResolver.requireOrgnummer(loggedInUser().hpr)

                val diagnoser =
                    when {
                        pasientId != null ->
                            konsultasjonService.getDiagnoser(call.patientId(), orgnummer)
                        konsultasjonId != null ->
                            konsultasjonService.getDiagnoser(call.konsultasjonId(), orgnummer)
                        else -> emptyList()
                    }
                call.respond(diagnoser)
            }
        }
    }
}
