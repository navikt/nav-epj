package no.nav.helse.helseId

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun localStubSession() =
    SessionResponse(idp = Idp.LOCAL_STUB.id, claims = mapOf("sub" to "local-dev"))

fun Route.sessionRoutes() {
    get("/api/session") {
        val principal = requireNotNull(call.principal<HelseIdPrincipal>()) { "User not found" }
        if (principal.idp == Idp.LOCAL_STUB) return@get call.respond(localStubSession())

        val idToken = requireNotNull(principal.idToken) { "Id token claims not found" }
        call.respond(
            SessionResponse(
                idp = Idp.HELSEID.id,
                claims = idToken.claims,
                issuedAt = idToken.issuedAt,
                expiresAt = idToken.expiresAt,
            )
        )
    }
}
