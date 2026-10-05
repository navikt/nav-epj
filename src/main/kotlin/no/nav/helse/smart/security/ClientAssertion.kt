package no.nav.helse.smart.security

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
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

private const val MAX_ASSERTION_LIFETIME_SECONDS = 300L

class ClientAssertionVerifier(
    private val env: Environment,
    private val jtiStore: ValkeyService,
    private val jwkSetProvider: ClientJwksSetProvider,
) {
    private val log = logger()

    suspend fun verify(client: SmartClient, params: Parameters): ErrorObject? {
        val jwkSource =
            jwkSourceFor(client) ?: return invalidClient("client has no registered jwks material")
        val jwt =
            extractAssertion(params, client.clientId)
                ?: return invalidClient("invalid client_assertion")
        validateHeader(jwt.header, client)?.let {
            return it
        }
        val claims =
            verifyClaims(client, jwt, jwkSource)
                ?: return invalidClient("client_assertion verification failed")
        return validateLifetime(claims) ?: claimJti(client.clientId, claims)
    }

    /**
     * An inline registered JWK Set (never fetched) takes precedence over a remote `jwksUri`, so a
     * deployed `nav-epj` never has to reach a participant's localhost.
     */
    private fun jwkSourceFor(client: SmartClient): JWKSource<SecurityContext>? =
        client.inlineJwkSet?.let { ImmutableJWKSet(it) }
            ?: client.jwksUri?.let { jwkSetProvider.sourceFor(it) }

    private suspend fun verifyClaims(
        client: SmartClient,
        jwt: SignedJWT,
        jwkSource: JWKSource<SecurityContext>,
    ): JWTClaimsSet? {
        val processor = buildProcessor(client, jwkSource)
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
                log.warn(
                    "SMART client_assertion parse failed for client {}: {}",
                    clientId,
                    it.message,
                )
            }
            .getOrNull()

    private fun buildProcessor(
        client: SmartClient,
        jwkSource: JWKSource<SecurityContext>,
    ): DefaultJWTProcessor<SecurityContext> =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector =
                JWSVerificationKeySelector(SUPPORTED_CLIENT_ASSERTION_ALGORITHMS, jwkSource)
            jwtClaimsSetVerifier =
                DefaultJWTClaimsVerifier(
                    "${env.smart.issuerBaseUrl}/token",
                    JWTClaimsSet.Builder().issuer(client.clientId).subject(client.clientId).build(),
                    setOf("iss", "sub", "aud", "exp", "jti"),
                )
        }

    private fun validateLifetime(claims: JWTClaimsSet): ErrorObject? {
        val now = Clock.System.now()
        val expiry = Instant.fromEpochMilliseconds(claims.expirationTime.time)
        return when {
            expiry <= now -> invalidClient("client_assertion has expired")
            expiry > now.plus(MAX_ASSERTION_LIFETIME_SECONDS.seconds) ->
                invalidClient("client_assertion exp too far in the future")
            else -> null
        }
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

    private fun validateHeader(header: JWSHeader, client: SmartClient): ErrorObject? =
        listOfNotNull(
                validateType(header),
                validateKid(header),
                validateAlgorithm(header),
                validateJku(header, client),
            )
            .firstOrNull()

    private fun validateType(header: JWSHeader): ErrorObject? =
        if (header.type?.type == "JWT") null
        else invalidClient("client_assertion missing or invalid typ, must be JWT")

    private fun validateKid(header: JWSHeader): ErrorObject? =
        if (header.keyID != null) null else invalidClient("client_assertion missing kid")

    private fun validateAlgorithm(header: JWSHeader): ErrorObject? =
        if (header.algorithm in SUPPORTED_CLIENT_ASSERTION_ALGORITHMS) null
        else invalidClient("unsupported client_assertion alg ${header.algorithm.name}")

    /**
     * A `jku` header claim is only meaningful for a client registered with a remote `jwksUri`, and
     * even then must match exactly what was registered. A client registered with an inline JWK Set
     * has no `jwksUri` to compare against, so any `jku` on its assertions is rejected rather than
     * trusted.
     */
    private fun validateJku(header: JWSHeader, client: SmartClient): ErrorObject? =
        header.jwkurl?.let { jku ->
            if (client.jwksUri != null && jku.toString() == client.jwksUri) null
            else invalidClient("client_assertion jku does not match registered jwks_uri")
        }

    private fun invalidClient(description: String): ErrorObject =
        OAuth2Error.INVALID_CLIENT.appendDescription(description)

    private fun Parameters.toNimbudsMultiMap(): Map<String, List<String>> =
        names().associateWith { getAll(it) ?: emptyList() }
}
