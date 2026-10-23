package no.nav.helse.epj.helsepersonell

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.helsepersonellHpr
import no.nav.helse.helseId.loggedInUser

fun Route.helsepersonellRoutes(helsepersonellService: HelsepersonellService) {

    route("/api") {
        route("/helsepersonell") {
            get("/me") {
                val principal = loggedInUser()
                val hpr = HelsepersonellHpr(principal.hpr)
                val loggedInUser =
                    helsepersonellService.findOrCreateHelsepersonell(hpr, principal.name)
                call.respond(loggedInUser)
            }
            get("/{hpr}") {
                val hpr = call.helsepersonellHpr()
                val helsepersonell = helsepersonellService.getHelsepersonell(hpr)
                call.respond(HttpStatusCode.OK, helsepersonell)
            }
        }
    }
}
