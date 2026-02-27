package no.nav.tsm.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import frontend.user.DebugInfo
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import frontend.user.HelseIdPrincipal
import frontend.user.User
import no.nav.tsm.utils.logger
import java.net.URI

fun Application.configureSecurity() {
    val log = logger()

    if (developmentMode) {
        configureLocalDevelopmentSecurity()
        return
    }

    val wellKnownUrl = environment.config.property("auth.helseId.wellKnownUrl").getString()
    val clientId = environment.config.property("auth.helseId.clientId").getString()

    val metadata = OIDCProviderMetadata.parse(URI(wellKnownUrl).toURL().readText())
    val jwkProvider = JwkProviderBuilder(metadata.jwkSetURI.toURL()).build()

    authentication {
        jwt("wonderwall-helseid") {
            realm = "nav-epj"
            verifier(jwkProvider, metadata.issuer.value)
            validate { credential ->
                val idToken = request.headers["X-Wonderwall-Id-Token"]
                if (idToken == null) {
                    log.warn("Missing X-Wonderwall-Id-Token header")
                    return@validate null
                }

                val decodedIdToken = JWT.decode(idToken)
                val hpr = decodedIdToken.getClaim("helseid://claims/hpr/hpr_number").asString()
                if (hpr == null) {
                    log.warn(
                        "Missing hpr_number claim in id_token, available claims: {}",
                        decodedIdToken.claims.keys
                    )
                    return@validate null
                }


                HelseIdPrincipal(
                    user = User(
                        name = decodedIdToken.getClaim("name").asString(),
                        hpr = hpr
                    ),
                    debug = DebugInfo(
                        idToken = idToken,
                        accessToken = request.headers["Authorization"]?.replace("Bearer ", "") ?: "missing",
                    )
                )
            }
        }
    }
}

private fun Application.configureLocalDevelopmentSecurity() {
    log.error("Local development security, if you see this in production, something is very wrong!")

    val stubPrincipal = HelseIdPrincipal(
        user = User(
            name = "Local Dev",
            hpr = "1234567"
        ),
        debug = DebugInfo(
            accessToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJsb2NhbC1kZXYifQ.",
            idToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJsb2NhbC1kZXYifQ."
        )

    )

    authentication {
        provider("wonderwall-helseid") {
            authenticate { ctx ->
                ctx.principal(stubPrincipal)
            }
        }
    }
}
