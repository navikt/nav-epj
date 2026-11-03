package no.nav.helse.epj.konsultasjon.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjonId
import no.nav.helse.epj.patientId
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.diagnoseRoutes(
    konsultasjonService: KonsultasjonService,
    sessionOrgResolver: SessionOrgResolver,
) {

    route("/api") {
        route("/diagnoser") {
            get {
                val patientId = call.parameters["patientId"]
                val konsultasjonId = call.parameters["konsultasjonId"]
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)

                val diagnoser =
                    when {
                        patientId != null ->
                            konsultasjonService.getDiagnoser(call.patientId(), orgNumber)
                        konsultasjonId != null ->
                            konsultasjonService.getDiagnoser(call.konsultasjonId(), orgNumber)
                        else -> emptyList()
                    }
                call.respond(diagnoser)
            }
        }
    }
}
