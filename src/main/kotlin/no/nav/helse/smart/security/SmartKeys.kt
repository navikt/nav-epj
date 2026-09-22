package no.nav.helse.smart.security

import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * This app's own RSA signing key for SMART access/id tokens, exposed as a public JWK at
 * `/oidc/jwks` (step 5).
 *
 * Sourced from the `smart.privateKeyJwk` config value (backed by a nais secret in production),
 * shared across all replicas. Used to both issue (`/oidc/token`) and verify
 * ([configureSmartSecurity]) tokens.
 */
class SmartKeys(privateKeyJwk: String) {
    private val rsaKey = JWK.parse(privateKeyJwk).toRSAKey()
    private val keyPair = rsaKey.toKeyPair()

    /** JOSE `kid`, so a verifier holding multiple keys can pick the right one. */
    val keyId: String = rsaKey.keyID
    val rsaPublic: RSAPublicKey = keyPair.public as RSAPublicKey

    /**
     * RS256 signer/verifier used to both sign (`/oidc/token`) and verify
     * (`configureSmartSecurity`).
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
