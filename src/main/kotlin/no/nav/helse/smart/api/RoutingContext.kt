package no.nav.helse.smart.api

import com.nimbusds.oauth2.sdk.ErrorObject
import com.nimbusds.oauth2.sdk.OAuth2Error
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger

private val logger = logger()

/**
 * Three ways to reject a request, matching three different spec requirements. Which to use depends
 * on where in the flow the failure happened:
 * - [rejectDirect]: `/authorize` or `/fhir/launch` failures *before* `redirect_uri` is validated.
 *   The server must not redirect here, since an unverified `redirect_uri` could be an open
 *   redirect.
 * - [rejectViaRedirect]: `/authorize` failures *after* `redirect_uri` is confirmed registered.
 *   Reported back to the app via a redirect carrying `error`/`error_description`/`state`.
 * - [rejectToken]: `/oidc/token` failures. A JSON error body (no user-agent is involved).
 */
suspend fun RoutingContext.rejectDirect(status: HttpStatusCode, reason: String) {
  logger.warn("SMART authorize rejected before redirect_uri was trusted: {}", reason)
  call.respond(status, reason)
}

suspend fun RoutingContext.rejectViaRedirect(
  redirectUri: String,
  state: String,
  error: ErrorObject,
) {
  logger.warn("SMART authorize rejected, redirecting back: error={}", error.code)
  val target =
    URLBuilder(redirectUri)
      .apply {
        // `error`: REQUIRED, a fixed authorization-endpoint code. Token-only codes
        // (invalid_grant/invalid_client) must never appear here.
        parameters.append("error", error.code)
        // `error_description`: OPTIONAL human-readable detail.
        error.description?.let { parameters.append("error_description", it) }
        // `state`: REQUIRED here since the app supplied one in its request.
        parameters.append("state", state)
      }
      .buildString()
  call.respondRedirect(target)
}

suspend fun RoutingContext.rejectToken(status: HttpStatusCode, error: ErrorObject) {
  logger.warn("SMART token request rejected: error={}", error.code)
  // `toJSONObject()` yields the `{"error", "error_description"}` body the token endpoint requires.
  call.respond(status, error.toJSONObject())
}

/** [rejectViaRedirect] shortcut for a missing required `/authorize` parameter. */
suspend fun RoutingContext.rejectMissingViaRedirect(
  redirectUri: String,
  state: String,
  param: String,
) =
  rejectViaRedirect(
    redirectUri,
    state,
    OAuth2Error.INVALID_REQUEST.appendDescription("missing $param"),
  )

/** [rejectToken] shortcut for a missing required `/token` parameter. */
suspend fun RoutingContext.rejectMissingToken(param: String) =
  rejectToken(
    HttpStatusCode.BadRequest,
    OAuth2Error.INVALID_REQUEST.appendDescription("missing $param"),
  )
