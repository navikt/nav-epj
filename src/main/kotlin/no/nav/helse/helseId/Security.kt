package no.nav.helse.helseId

import com.auth0.jwt.JWT
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import java.net.URI
import no.nav.helse.core.utils.logger

private val log = logger()

fun Application.configureHelseId() {
    val localStub = environment.config.propertyOrNull("auth.localStub")?.getString().toBoolean()
    if (localStub) {
        log.error(
            "Local development security, if you see this in production, something is very wrong!"
        )
        configureLocalDevelopmentSecurity()
        return
    }
    val wellKnownUrl = environment.config.property("auth.helseId.wellKnownUrl").getString()

    val metadata = OIDCProviderMetadata.parse(URI(wellKnownUrl).toURL().readText())
    val verifier =
        HelseIdTokenVerifier(
            issuer = metadata.issuer.value,
            jwkSource =
                JWKSourceBuilder.create<SecurityContext>(metadata.jwkSetURI.toURL())
                    .retrying(true)
                    .build(),
        )

    authentication { helseId(verifier) }
}

internal fun AuthenticationConfig.helseId(verifier: HelseIdTokenVerifier) {
    provider("wonderwall-helseid") {
        authenticate { ctx ->
            val principal = ctx.helseIdPrincipal(verifier)
            if (principal != null) {
                ctx.principal(principal)
            } else {
                ctx.challenge("wonderwall-helseid", AuthenticationFailedCause.InvalidCredentials) {
                    challenge,
                    call ->
                    call.respond(UnauthorizedResponse())
                    challenge.complete()
                }
            }
        }
    }
}

private suspend fun AuthenticationContext.helseIdPrincipal(
    verifier: HelseIdTokenVerifier
): HelseIdPrincipal? {
    val accessToken = (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)?.blob
    if (accessToken == null) {
        log.debug("Missing bearer access token")
        return null
    }
    verifier.verify(accessToken) ?: return null

    val idToken = call.request.headers["X-Wonderwall-Id-Token"]
    if (idToken == null) {
        log.warn("Missing X-Wonderwall-Id-Token header")
        return null
    }

    val decodedIdToken = JWT.decode(idToken)
    val hpr = decodedIdToken.getClaim("helseid://claims/hpr/hpr_number").asString()
    if (hpr == null) {
        log.warn(
            "Missing hpr_number claim in id_token, available claims: {}",
            decodedIdToken.claims.keys,
        )
        return null
    }

    return HelseIdPrincipal(
        user = User(name = decodedIdToken.getClaim("name").asString(), hpr = hpr),
        debug = DebugInfo(idToken = idToken, accessToken = accessToken),
        idToken = decodedIdToken.toIdTokenClaims(),
    )
}

private fun Application.configureLocalDevelopmentSecurity() {
    log.error("Local development security, if you see this in production, something is very wrong!")

    val stubPrincipal =
        HelseIdPrincipal(
            user = User(name = "GRØNN VITS", hpr = "565501872"),
            debug =
                DebugInfo(
                    accessToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJsb2NhbC1kZXYifQ.",
                    idToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJsb2NhbC1kZXYifQ.",
                ),
            idp = Idp.LOCAL_STUB,
        )

    authentication {
        provider("wonderwall-helseid") { authenticate { ctx -> ctx.principal(stubPrincipal) } }
    }
}
