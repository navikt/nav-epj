package no.nav.helse.smart.api

import com.nimbusds.oauth2.sdk.ErrorObject
import com.nimbusds.oauth2.sdk.OAuth2Error
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger

private val logger = logger()

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
        parameters.append("error", error.code)
        error.description?.let { parameters.append("error_description", it) }
        parameters.append("state", state)
      }
      .buildString()
  call.respondRedirect(target)
}

suspend fun RoutingContext.rejectToken(status: HttpStatusCode, error: ErrorObject) {
  logger.warn("SMART token request rejected: error={}", error.code)
  call.respond(status, error.toJSONObject())
}

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

suspend fun RoutingContext.rejectMissingToken(param: String) =
  rejectToken(
    HttpStatusCode.BadRequest,
    OAuth2Error.INVALID_REQUEST.appendDescription("missing $param"),
  )
