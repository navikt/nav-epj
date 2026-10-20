package no.nav.helse.epj.sessionorg

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.helseId.loggedInUser

data class SessionOrgDto(val id: String, val navn: String, val orgnummer: String, val tlf: String?)

data class ChoosableOrgDto(val org: SessionOrgDto, val hasOwnList: Boolean)

data class SessionOrgResponse(
    val org: SessionOrgDto?,
    val needsChoice: Boolean,
    val canChoose: Boolean,
    val choosableOrgs: List<ChoosableOrgDto>,
)

data class ChooseOrgRequest(val orgnummer: String)

private fun Legekontor.toDto() =
    SessionOrgDto(id.value.toString(), navn, checkNotNull(orgnummer), tlf)

fun Route.sessionOrgRoutes(
    sessionOrgResolver: SessionOrgResolver,
    sessionOrgService: SessionOrgService,
) {
    suspend fun response(hpr: String): SessionOrgResponse {
        val resolution = sessionOrgResolver.resolve(hpr)
        val org = (resolution as? SessionOrgResolution.Resolved)?.org
        val derived = (resolution as? SessionOrgResolution.Resolved)?.derived == true
        return SessionOrgResponse(
            org = org?.toDto(),
            needsChoice = resolution is SessionOrgResolution.NeedsChoice,
            canChoose = !derived,
            choosableOrgs =
                sessionOrgResolver.choosableOrgs(hpr).map {
                    ChoosableOrgDto(it.org.toDto(), it.hasOwnList)
                },
        )
    }

    route("/api/session-org") {
        get { call.respond(response(loggedInUser().hpr)) }
        put {
            val hpr = loggedInUser().hpr
            val request = call.receive<ChooseOrgRequest>()
            when (sessionOrgService.choose(hpr, request.orgnummer)) {
                is ChooseOrgResult.Chosen -> call.respond(response(hpr))
                ChooseOrgResult.UnknownOrg ->
                    call.respond(HttpStatusCode.BadRequest, "Ukjent legekontor")
                ChooseOrgResult.DerivedFromList ->
                    call.respond(
                        HttpStatusCode.Conflict,
                        "Legekontoret er gitt av fastlegelisten og kan ikke endres",
                    )
            }
        }
    }
}
