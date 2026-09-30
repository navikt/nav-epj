package no.nav.helse.helseId

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

private const val ID_TOKEN_HEADER = "X-Wonderwall-Id-Token"
private const val HPR_CLAIM = "helseid://claims/hpr/hpr_number"

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SessionResponse(
    val idp: String,
    val claims: Map<String, String>,
    val issuedAt: Instant? = null,
    val expiresAt: Instant? = null,
)

private fun localStubSession() =
    SessionResponse(idp = Idp.LOCAL_STUB.id, claims = mapOf("sub" to "local-dev"))

private fun DecodedJWT.toSessionResponse(): SessionResponse {
    val claims = buildMap {
        issuer?.let { put("iss", it) }
        if (!audience.isNullOrEmpty()) put("aud", audience.joinToString(", "))
        getClaim("name").asString()?.let { put("name", it) }
        getClaim(HPR_CLAIM).asString()?.let { put(HPR_CLAIM, it) }
    }
    return SessionResponse(
        idp = Idp.HELSEID.id,
        claims = claims,
        issuedAt = issuedAtAsInstant,
        expiresAt = expiresAtAsInstant,
    )
}

fun Route.sessionRoutes() {
    get("/api/session") {
        val principal = requireNotNull(call.principal<HelseIdPrincipal>()) { "User not found" }
        if (principal.idp == Idp.LOCAL_STUB) return@get call.respond(localStubSession())

        val decoded =
            call.request.headers[ID_TOKEN_HEADER]?.let {
                try {
                    JWT.decode(it)
                } catch (_: JWTDecodeException) {
                    null
                }
            } ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respond(decoded.toSessionResponse())
    }
}
