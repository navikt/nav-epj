package no.nav.helse.smart.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk
import java.net.URI
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test

private const val JWKS_URI = "https://client.example.com/jwks"
private const val CLIENT_ID = "test-client-id"

private fun dateIn(delta: Duration) = Date(Clock.System.now().plus(delta).toEpochMilliseconds())

class ClientAssertionVerifierTest {

  private val key = ECKeyGenerator(Curve.P_384).keyID("test-kid").generate()
  private val client =
    SmartClient(
      clientId = CLIENT_ID,
      redirectUris = listOf("http://test"),
      launchUris = listOf("http://test/fhir/launch"),
      tokenEndpointAuthMethod = TokenEndpointAuthMethod.PRIVATE_KEY_JWT,
      jwksUri = JWKS_URI,
      allowedScopes =
        parseRegisteredScopes(listOf("openid", "fhirUser", "launch", "patient/*.cruds")),
    )
  private val jtiStore = mockk<ValkeyService>()
  private val verifier =
    ClientAssertionVerifier(
      env = simpleTestEnvironment,
      jtiStore = jtiStore,
      jwkSetProvider = { ImmutableJWKSet(JWKSet(key.toPublicJWK())) },
    )

  private fun assertion(
    alg: JWSAlgorithm = JWSAlgorithm.ES384,
    typ: String? = "JWT",
    kid: String? = key.keyID,
    jku: String? = null,
    iss: String = CLIENT_ID,
    sub: String = CLIENT_ID,
    aud: String = "${simpleTestEnvironment.smart.issuerBaseUrl}/token",
    exp: Date = dateIn(60.seconds),
    jti: String? = "jti-${System.nanoTime()}",
  ): String {
    val header =
      JWSHeader.Builder(alg)
        .apply {
          if (typ != null) type(JOSEObjectType.JWT)
          if (kid != null) keyID(kid)
          if (jku != null) jwkURL(URI(jku))
        }
        .build()

    val claims =
      JWTClaimsSet.Builder()
        .issuer(iss)
        .subject(sub)
        .audience(aud)
        .expirationTime(exp)
        .apply { if (jti != null) jwtID(jti) }
        .build()
    return SignedJWT(header, claims).apply { sign(ECDSASigner(key)) }.serialize()
  }

  private fun paramsWith(assertion: String) = Parameters.build {
    append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
    append("client_assertion", assertion)
  }

  @Test
  fun `valid client_assertion is accepted`() = runTest {
    coEvery { jtiStore.setIfAbsent(any(), any(), any()) } returns true
    val result = verifier.verify(client, paramsWith(assertion()))
    assertNull(result)
  }

  @Test
  fun `client without registered jwks_uri is rejected`() = runTest {
    val result = verifier.verify(client.copy(jwksUri = null), paramsWith(assertion()))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `wrong signing key is rejected`() = runTest {
    val otherKey = ECKeyGenerator(Curve.P_384).keyID("other-kid").generate()
    val forged =
      SignedJWT(
          JWSHeader.Builder(JWSAlgorithm.ES384).keyID("other-kid").type(JOSEObjectType.JWT).build(),
          JWTClaimsSet.Builder()
            .issuer(CLIENT_ID)
            .subject(CLIENT_ID)
            .audience("${simpleTestEnvironment.smart.issuerBaseUrl}/token")
            .expirationTime(dateIn(60.seconds))
            .jwtID("jti-1")
            .build(),
        )
        .apply { sign(ECDSASigner(otherKey)) }
        .serialize()
    val result = verifier.verify(client, paramsWith(forged))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `missing typ is rejected`() = runTest {
    val result = verifier.verify(client, paramsWith(assertion(typ = null)))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `missing kid is rejected`() = runTest {
    val result = verifier.verify(client, paramsWith(assertion(kid = null)))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `unsupported alg is rejected`() = runTest {
    val rsaKey = RSAKeyGenerator(2048).keyID("rsa-kid").generate()
    val forged =
      SignedJWT(
          JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-kid").type(JOSEObjectType.JWT).build(),
          JWTClaimsSet.Builder()
            .issuer(CLIENT_ID)
            .subject(CLIENT_ID)
            .audience("${simpleTestEnvironment.smart.issuerBaseUrl}/token")
            .expirationTime(dateIn(60.seconds))
            .jwtID("jti-2")
            .build(),
        )
        .apply { sign(RSASSASigner(rsaKey.toRSAPrivateKey())) }
        .serialize()
    val result = verifier.verify(client, paramsWith(forged))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `wrong jku is rejected`() = runTest {
    val result =
      verifier.verify(client, paramsWith(assertion(jku = "https://dr-evil.example.org/jwks")))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `wrong iss is rejected`() = runTest {
    val result = verifier.verify(client, paramsWith(assertion(iss = "other-client")))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `exp too far in the future is rejected`() = runTest {
    val result = verifier.verify(client, paramsWith(assertion(exp = dateIn(600.seconds))))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `missing jti is rejected`() = runTest {
    coEvery { jtiStore.setIfAbsent(any(), any(), any()) } returns true
    val result = verifier.verify(client, paramsWith(assertion(jti = null)))
    assertEquals("invalid_client", result?.code)
  }

  @Test
  fun `reused jti is rejected`() = runTest {
    coEvery { jtiStore.setIfAbsent(any(), any(), any()) } returns false
    val result = verifier.verify(client, paramsWith(assertion(jti = "reused_jti")))
    assertEquals("invalid_client", result?.code)
  }
}
