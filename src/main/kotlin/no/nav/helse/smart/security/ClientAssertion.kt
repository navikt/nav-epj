package no.nav.helse.smart.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.oauth2.sdk.ErrorObject
import com.nimbusds.oauth2.sdk.OAuth2Error
import com.nimbusds.oauth2.sdk.auth.PrivateKeyJWT
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.core.Environment
import no.nav.helse.core.utils.logger
import no.nav.helse.smart.valkey.ValkeyService

private val SUPPORTED_ALGORITHMS = setOf(JWSAlgorithm.RS384, JWSAlgorithm.ES384)
private const val MAX_ASSERTION_LIFETIME_SECONDS = 300L

class ClientAssertionVerifier(
  private val env: Environment,
  private val jtiStore: ValkeyService,
  private val jwkSetProvider: ClientJwksSetProvider,
) {
  private val log = logger()

  suspend fun verify(client: SmartClient, params: Parameters): ErrorObject? {
    val jwksUri = client.jwksUri ?: return invalidClient("client has no registered jwks_uri")
    val jwt =
      extractAssertion(params, client.clientId) ?: return invalidClient("invalid client_assertion")
    validateHeader(jwt.header, jwksUri)?.let {
      return it
    }
    val claims =
      verifyClaims(client, jwt, jwksUri)
        ?: return invalidClient("client_assertion verification failed")
    return validateLifetime(claims) ?: claimJti(client.clientId, claims)
  }

  private suspend fun verifyClaims(
    client: SmartClient,
    jwt: SignedJWT,
    jwksUri: String,
  ): JWTClaimsSet? {
    val processor = buildProcessor(client, jwksUri)
    return runCatching { withContext(Dispatchers.IO) { processor.process(jwt, null) } }
      .onFailure {
        log.warn(
          "SMART client_assertion verification failed for client={}: {}",
          client.clientId,
          it.message,
        )
      }
      .getOrNull()
  }

  private fun extractAssertion(params: Parameters, clientId: String): SignedJWT? =
    runCatching { PrivateKeyJWT.parse(params.toNimbudsMultiMap()).clientAssertion }
      .onFailure {
        log.warn("SMART client_assertion parse failed for client {}: {}", clientId, it.message)
      }
      .getOrNull()

  private fun buildProcessor(
    client: SmartClient,
    jwksUri: String,
  ): DefaultJWTProcessor<SecurityContext> =
    DefaultJWTProcessor<SecurityContext>().apply {
      jwsKeySelector =
        JWSVerificationKeySelector(SUPPORTED_ALGORITHMS, jwkSetProvider.sourceFor(jwksUri))
      jwtClaimsSetVerifier =
        DefaultJWTClaimsVerifier(
          "${env.smart.issuerBaseUrl}/token",
          JWTClaimsSet.Builder().issuer(client.clientId).subject(client.clientId).build(),
          setOf("iss", "sub", "aud", "exp", "jti"),
        )
    }

  private fun validateLifetime(claims: JWTClaimsSet): ErrorObject? {
    val maxAllowedExpiry = Clock.System.now().plus(MAX_ASSERTION_LIFETIME_SECONDS.seconds)
    val expiry = Instant.fromEpochMilliseconds(claims.expirationTime.time)
    return if (expiry <= maxAllowedExpiry) null
    else invalidClient("client_assertion exp too far in the future")
  }

  private suspend fun claimJti(clientId: String, claims: JWTClaimsSet): ErrorObject? {
    val jti = claims.jwtid ?: return invalidClient("client_assertion missing jti")
    val key = "smart:assertion-jti:$clientId:$jti"
    if (jtiStore.setIfAbsent(key, "", ttlSeconds = MAX_ASSERTION_LIFETIME_SECONDS)) {
      return null
    }
    log.warn("SMART client_assertion replay detected for client={} jti={}", clientId, jti)
    return invalidClient("client_assertion jti already used")
  }

  private fun validateHeader(header: JWSHeader, jwksUri: String): ErrorObject? =
    listOfNotNull(
        validateType(header),
        validateKid(header),
        validateAlgorithm(header),
        validateJku(header, jwksUri),
      )
      .firstOrNull()

  private fun validateType(header: JWSHeader): ErrorObject? =
    if (header.type?.type == "JWT") null
    else invalidClient("client_assertion missing or invalid typ, must be JWT")

  private fun validateKid(header: JWSHeader): ErrorObject? =
    if (header.keyID != null) null else invalidClient("client_assertion missing kid")

  private fun validateAlgorithm(header: JWSHeader): ErrorObject? =
    if (header.algorithm in SUPPORTED_ALGORITHMS) null
    else invalidClient("unsupported client_assertion alg ${header.algorithm.name}")

  private fun validateJku(header: JWSHeader, jwksUri: String): ErrorObject? =
    header.jwkurl?.let { jku ->
      if (jku.toString() == jwksUri) null
      else invalidClient("client_assertion jku does not match registered jwks_uri")
    }

  private fun invalidClient(description: String): ErrorObject =
    OAuth2Error.INVALID_CLIENT.appendDescription(description)

  private fun Parameters.toNimbudsMultiMap(): Map<String, List<String>> =
    names().associateWith { getAll(it) ?: emptyList() }
}
