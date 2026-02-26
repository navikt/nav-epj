package no.nav.tsm.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.tsm.frontend.wonderwall.HelseIdPrincipal
import no.nav.tsm.frontend.wonderwall.User
import java.net.URI

fun Application.configureSecurity() {
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
            verifier(jwkProvider, metadata.issuer.value) {
                acceptLeeway(10)
                withAudience(clientId)
            }
            validate { credential ->
                if (!credential.payload.audience.contains(clientId)) return@validate null

                val idToken = request.headers["X-Wonderwall-Id-Token"] ?: return@validate null
                val hpr =
                    JWT.decode(idToken).getClaim("helseid://claims/hpr/hpr_number").asString() ?: return@validate null

                HelseIdPrincipal(
                    user = User(hpr = hpr),
                    accessToken = JWTPrincipal(credential.payload),
                )
            }
        }
    }
}

private fun Application.configureLocalDevelopmentSecurity() {
    log.error("Local development security, if you see this in production, something is very wrong!")

    val stubPrincipal = HelseIdPrincipal(
        user = User(hpr = "1234567"),
        accessToken = JWTPrincipal(JWT.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiJsb2NhbC1kZXYifQ.")),
    )

    authentication {
        provider("wonderwall-helseid") {
            authenticate { ctx ->
                ctx.principal(stubPrincipal)
            }
        }
    }
}
