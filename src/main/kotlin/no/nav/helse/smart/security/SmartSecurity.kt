package no.nav.helse.smart.security

import com.auth0.jwt.JWT
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
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
      verifier(
        JWT.require(SmartKeys.algorithm)
          .withIssuer(env.smart.issuerBaseUrl)
          .withAudience(env.smart.fhirServerUrl)
          .build()
      )
      validate { credentials ->
        val token = request.bearerToken() ?: return@validate null
        if (runCatching { JWT.decode(token).type }.getOrNull() != "at+jwt") {
          return@validate null
        }

        val scopes = credentials.payload.getClaim("scope").asString() ?: return@validate null

        SmartPrincipal(
          subject = credentials.payload.subject ?: return@validate null,
          scopes = parseScopes(scopes),
          patient = credentials.payload.getClaim("patient").asString(),
          encounter = credentials.payload.getClaim("encounter").asString(),
        )
      }
    }
  }
}

private fun ApplicationRequest.bearerToken(): String? =
  (parseAuthorizationHeader() as? HttpAuthHeader.Single)
    ?.takeIf { it.authScheme.equals(AuthScheme.Bearer, ignoreCase = true) }
    ?.blob
