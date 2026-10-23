package no.nav.helse.smart.security

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.ErrorObject
import com.nimbusds.oauth2.sdk.OAuth2Error
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import java.net.URLDecoder
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
    val clientSecret =
        client.clientSecret ?: return invalidClient("client has no registered secret")
    val (username, password) = basicCredentials(request) ?: return OAuth2Error.INVALID_CLIENT
    val authenticated =
        username == client.clientId &&
            MessageDigest.isEqual(
                password.toByteArray(Charsets.UTF_8),
                clientSecret.toByteArray(Charsets.UTF_8),
            )
    return if (authenticated) null else OAuth2Error.INVALID_CLIENT
}

private fun basicClientId(request: ApplicationRequest): String? =
    rawBasicCredentials(request)?.first?.let { id ->
        runCatching { formDecode(id) }.getOrDefault(id)
    }

private fun basicCredentials(request: ApplicationRequest): Pair<String, String>? =
    rawBasicCredentials(request)?.let { (id, secret) ->
        runCatching { formDecode(id) to formDecode(secret) }.getOrNull()
    }

private fun formDecode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

private fun rawBasicCredentials(request: ApplicationRequest): Pair<String, String>? =
    runCatching { request.parseAuthorizationHeader() }
        .getOrNull()
        .let { it as? HttpAuthHeader.Single }
        ?.takeIf { it.authScheme == AuthScheme.Basic }
        ?.let { runCatching { String(Base64.getDecoder().decode(it.blob)) }.getOrNull() }
        ?.takeIf { ":" in it }
        ?.let { it.substringBefore(":") to it.substringAfter(":") }

private fun invalidClient(description: String): ErrorObject =
    OAuth2Error.INVALID_CLIENT.withDetail(description)
