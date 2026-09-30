package no.nav.helse.helseId

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import java.time.Instant

const val HPR_CLAIM = "helseid://claims/hpr/hpr_number"

data class User(val name: String, val hpr: String)

enum class Idp(val id: String) {
    HELSEID("helseid"),
    LOCAL_STUB("local-stub"),
}

data class IdTokenClaims(
    val claims: Map<String, String>,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
)

fun DecodedJWT.toIdTokenClaims(): IdTokenClaims {
    val claims = buildMap {
        issuer?.let { put("iss", it) }
        if (!audience.isNullOrEmpty()) put("aud", audience.joinToString(", "))
        getClaim("name").asString()?.let { put("name", it) }
        getClaim(HPR_CLAIM).asString()?.let { put(HPR_CLAIM, it) }
    }
    return IdTokenClaims(claims, issuedAtAsInstant, expiresAtAsInstant)
}

data class HelseIdPrincipal(
    val user: User,
    val debug: DebugInfo,
    val idp: Idp = Idp.HELSEID,
    val idToken: IdTokenClaims? = null,
)

data class DebugInfo(val accessToken: String, val idToken: String)

fun RoutingContext.loggedInUser(): User {
    val principal =
        requireNotNull(this.call.principal<HelseIdPrincipal>()) { "User not found in principal" }

    return principal.user
}
