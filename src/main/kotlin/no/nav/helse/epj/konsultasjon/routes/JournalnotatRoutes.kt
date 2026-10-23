package no.nav.helse.epj.konsultasjon.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.journalnotatId
import no.nav.helse.epj.konsultasjon.Journalnotat
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgnummer
import no.nav.helse.helseId.loggedInUser

fun Route.journalnotatRoutes(
    konsultasjonService: KonsultasjonService,
    sessionOrgResolver: SessionOrgResolver,
) {

    val log = logger()

    route("/api") {
        route("/journalnotat") {
            get("/{journalnotatId}") {
                val id = call.journalnotatId()
                val orgnummer = sessionOrgResolver.requireOrgnummer(loggedInUser().hpr)
                val journalnotat =
                    konsultasjonService.getJournalnotat(id, orgnummer)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                log.info("journalnotat: $journalnotat")
                call.respond(journalnotat)
            }
            post {
                val request = call.receive<Journalnotat>()
                val orgnummer = sessionOrgResolver.requireOrgnummer(loggedInUser().hpr)
                if (konsultasjonService.createJournalnotat(request, orgnummer)) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Kunne ikke opprette journalnotat",
                    )
                }
            }
        }
    }
}
