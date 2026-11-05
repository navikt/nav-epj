package no.nav.helse.helseId

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.util.Date

object HelseIdTestTokens {
    const val ISSUER = "https://helseid-sts.test.nhn.no"
    private const val KID = "23AAF565A131CE4F1C3B324218DA49BB4A8548AD"
    private val key: RSAKey = RSAKeyGenerator(2048).keyID(KID).generate()

    fun verifier() =
        HelseIdTokenVerifier(
            issuer = ISSUER,
            jwkSource =
                ImmutableJWKSet(
                    JWKSet(
                        listOf(
                            RSAKey.Builder(key.toPublicJWK()).algorithm(JWSAlgorithm.PS256).build(),
                            RSAKey.Builder(key.toPublicJWK()).algorithm(JWSAlgorithm.RS256).build(),
                        )
                    )
                ),
        )

    fun accessToken(alg: JWSAlgorithm = JWSAlgorithm.RS256, iss: String = ISSUER): String =
        SignedJWT(
                JWSHeader.Builder(alg).keyID(KID).type(JOSEObjectType("at+jwt")).build(),
                JWTClaimsSet.Builder()
                    .issuer(iss)
                    .subject("someone")
                    .expirationTime(Date(System.currentTimeMillis() + 60_000))
                    .build(),
            )
            .apply { sign(RSASSASigner(key)) }
            .serialize()

    fun idToken(hpr: String): String =
        SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .claim("name", "GRØNN VITS")
                    .claim("helseid://claims/hpr/hpr_number", hpr)
                    .build(),
            )
            .apply { sign(RSASSASigner(key)) }
            .serialize()
}
