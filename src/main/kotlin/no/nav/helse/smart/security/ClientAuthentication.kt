package no.nav.helse.smart.security

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.ErrorObject
import com.nimbusds.oauth2.sdk.OAuth2Error
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import java.security.MessageDigest
import java.util.*

fun resolveAssertedClientId(request: ApplicationRequest, params: Parameters): String? =
  params["client_assertion"]?.let {
    runCatching { SignedJWT.parse(it).jwtClaimsSet.subject }.getOrNull()
  } ?: basicClientId(request) ?: params["client_id"]

suspend fun authenticateClient(
  request: ApplicationRequest,
  client: SmartClient,
  params: Parameters,
  assertionVerifier: ClientAssertionVerifier,
): ErrorObject? =
  when (client.tokenEndpointAuthMethod) {
    TokenEndpointAuthMethod.NONE -> null
    TokenEndpointAuthMethod.CLIENT_SECRET_BASIC -> verifyClientSecretBasic(request, client)
    TokenEndpointAuthMethod.PRIVATE_KEY_JWT -> assertionVerifier.verify(client, params)
  }

fun verifyClientSecretBasic(request: ApplicationRequest, client: SmartClient): ErrorObject? {
  val clientSecret = client.clientSecret ?: return invalidClient("client has no registered secret")
  val (username, password) =
    basicCredentials(request) ?: return invalidClient("client authentication failed")
  val authenticated =
    username == client.clientId &&
      MessageDigest.isEqual(
        password.toByteArray(Charsets.UTF_8),
        clientSecret.toByteArray(Charsets.UTF_8),
      )
  return if (authenticated) null else invalidClient("client authentication failed")
}

private fun basicClientId(request: ApplicationRequest): String? = basicCredentials(request)?.first

private fun basicCredentials(request: ApplicationRequest): Pair<String, String>? =
  (request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
    ?.takeIf { it.authScheme == AuthScheme.Basic }
    ?.let { String(Base64.getDecoder().decode(it.blob)) }
    ?.let { it.substringBefore(":") to it.substringAfter(":") }

private fun invalidClient(description: String): ErrorObject =
  OAuth2Error.INVALID_CLIENT.appendDescription(description)
