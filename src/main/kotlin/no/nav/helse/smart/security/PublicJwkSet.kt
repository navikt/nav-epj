package no.nav.helse.smart.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyOperation
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey

/**
 * Signing algorithms this server's client_assertion verifier actually accepts
 * ([ClientAssertionVerifier]). Shared with inline JWK Set validation so registration never accepts
 * a key the verifier could not use.
 */
val SUPPORTED_CLIENT_ASSERTION_ALGORITHMS: Set<JWSAlgorithm> =
    setOf(JWSAlgorithm.RS384, JWSAlgorithm.ES384)

private val FORBIDDEN_PUBLIC_KEY_OPS =
    setOf(
        KeyOperation.SIGN,
        KeyOperation.DECRYPT,
        KeyOperation.UNWRAP_KEY,
        KeyOperation.DERIVE_KEY,
        KeyOperation.DERIVE_BITS,
    )

/**
 * Parses and validates an inline public JWK Set registered for a `private_key_jwt` client (step 7's
 * typed client registry). Never fetched over the network: this is exactly the material a deployed
 * `nav-epj` uses to verify a participant's `client_assertion`, so it must contain public keys only.
 *
 * Fails with a clear [IllegalArgumentException] for anything the verifier could not safely use:
 * malformed JSON, no keys, private key material, missing/duplicate `kid`, a non-signature `use`,
 * private key_ops, or a key type/curve/algorithm the verifier does not accept.
 */
fun parsePublicJwkSet(clientId: String, json: String): JWKSet {
    val jwkSet =
        runCatching { JWKSet.parse(json) }
            .getOrElse {
                throw IllegalArgumentException(
                    "smart.clients: client '$clientId' has a malformed inline jwkSet: ${it.message}"
                )
            }
    require(jwkSet.keys.isNotEmpty()) {
        "smart.clients: client '$clientId' has an inline jwkSet with no keys"
    }
    val seenKids = mutableSetOf<String>()
    jwkSet.keys.forEach { validatePublicJwk(clientId, it, seenKids) }
    return jwkSet
}

private fun validatePublicJwk(clientId: String, key: JWK, seenKids: MutableSet<String>) {
    require(!key.isPrivate) {
        "smart.clients: client '$clientId' inline jwkSet contains private key material " +
            "(kid '${key.keyID}'); only public keys are permitted"
    }
    val kid = key.keyID
    require(!kid.isNullOrBlank()) {
        "smart.clients: client '$clientId' inline jwkSet has a key with no kid"
    }
    require(seenKids.add(kid)) {
        "smart.clients: client '$clientId' inline jwkSet has duplicate kid '$kid'"
    }
    key.keyUse?.let { use ->
        require(use == KeyUse.SIGNATURE) {
            "smart.clients: client '$clientId' inline jwkSet key '$kid' has use '$use', must be 'sig'"
        }
    }
    key.keyOperations?.let { ops ->
        val forbidden = ops intersect FORBIDDEN_PUBLIC_KEY_OPS
        require(forbidden.isEmpty()) {
            "smart.clients: client '$clientId' inline jwkSet key '$kid' declares key_ops " +
                "$forbidden, which are private-key operations not permitted on a public key"
        }
    }
    key.algorithm?.let { alg ->
        require(SUPPORTED_CLIENT_ASSERTION_ALGORITHMS.any { it.name == alg.name }) {
            "smart.clients: client '$clientId' inline jwkSet key '$kid' declares unsupported " +
                "alg '$alg'; only ${SUPPORTED_CLIENT_ASSERTION_ALGORITHMS.joinToString()} are accepted"
        }
    }
    when (key) {
        is RSAKey -> Unit // any RSA key can be used with RS384
        is ECKey ->
            require(key.curve == Curve.P_384) {
                "smart.clients: client '$clientId' inline jwkSet key '$kid' uses curve " +
                    "${key.curve}; only P-384 (for ES384) is supported"
            }
        else ->
            throw IllegalArgumentException(
                "smart.clients: client '$clientId' inline jwkSet key '$kid' has unsupported key " +
                    "type ${key.keyType}; only RSA and EC keys are supported"
            )
    }
}
