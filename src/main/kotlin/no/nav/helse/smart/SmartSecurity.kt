package no.nav.helse.smart

import com.auth0.jwt.JWT
import io.ktor.server.application.*
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.core.Environment

/**
 * Step 6 ("Access FHIR API"): the `"smart-access-token"` JWT auth provider that guards this
 * server's FHIR routes, verifying access tokens minted by `/oidc/token`.
 */
fun Application.configureSmartSecurity() {
  val env: Environment by dependencies

  authentication {
    jwt("smart-access-token") {
      realm = "fhir"
      verifier(JWT.require(SmartKeys.algorithm).withIssuer(env.smart.issuerBaseUrl).build())
      validate { credentials ->
        val scope = credentials.payload.getClaim("scope").asString() ?: return@validate null
        val hasFhirScope =
          scope.contains("patient/") || scope.contains("user/") || scope.contains("system/")
        if (!hasFhirScope) {
          return@validate null
        }

        SmartPrincipal(
          subject = credentials.payload.subject ?: return@validate null,
          scope = scope,
          patient = credentials.payload.getClaim("patient").asString(),
          encounter = credentials.payload.getClaim("encounter").asString(),
        )
      }
    }
  }
}
