package no.nav.helse.epj.legekontor

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.legekontorId
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.requireOrgnummer
import no.nav.helse.helseId.loggedInUser

fun Route.legekontorRoutes(
    legekontorService: LegekontorService,
    sessionOrgResolver: SessionOrgResolver,
) {
    route("api") {
        get("/legekontor/{legekontorId}") {
            val idParam = call.legekontorId()
            val orgnummer = sessionOrgResolver.requireOrgnummer(loggedInUser().hpr)
            val legekontor = legekontorService.getLegekontor(idParam, orgnummer)
            call.respond(legekontor)
        }
    }
}
