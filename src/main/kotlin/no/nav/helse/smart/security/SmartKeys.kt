package no.nav.helse.smart.security

import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

/**
 * This app's own RSA signing key for SMART access/id tokens, exposed as a public JWK at
 * `/oidc/jwks` (step 5).
 *
 * Generated fresh in memory once per process; never persisted or shared. Fine for a single instance
 * that both issues (`/oidc/token`) and verifies ([configureSmartSecurity]) tokens.
 *
 * TODO Kubernetes: each replica would generate its own key, so tokens issued by one TODO replica
 * could not be verified by another (nor would its `/oidc/jwks` list the other's key).
 */
internal object SmartKeys {
  private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()

  /** JOSE `kid`, so a verifier holding multiple keys can pick the right one. */
  val keyId: String = UUID.randomUUID().toString()
  val rsaPublic: RSAPublicKey = keyPair.public as RSAPublicKey

  /**
   * RS256 signer/verifier used to both sign (`/oidc/token`) and verify (`configureSmartSecurity`).
   */
  val algorithm: Algorithm = Algorithm.RSA256(rsaPublic, keyPair.private as RSAPrivateKey)

  /** Public JWK served (public key only) at `GET /oidc/jwks`. */
  val jwk: RSAKey =
    RSAKey.Builder(rsaPublic)
      .privateKey(keyPair.private as RSAPrivateKey)
      .keyUse(KeyUse.SIGNATURE)
      .keyID(keyId)
      .algorithm(JWSAlgorithm.RS256)
      .build()
}
