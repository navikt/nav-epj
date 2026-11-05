package no.nav.helse.helseId

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.core.utils.logger

class HelseIdTokenVerifier(issuer: String, jwkSource: JWKSource<SecurityContext>) {
    private val log = logger()

    private val processor =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsTypeVerifier =
                DefaultJOSEObjectTypeVerifier(JOSEObjectType.JWT, JOSEObjectType("at+jwt"), null)
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.Family.RSA, jwkSource)
            jwtClaimsSetVerifier =
                DefaultJWTClaimsVerifier(
                    null,
                    JWTClaimsSet.Builder().issuer(issuer).build(),
                    setOf("iss", "exp"),
                )
        }

    suspend fun verify(token: String): JWTClaimsSet? =
        runCatching {
                val jwt = SignedJWT.parse(token)
                log.debug(
                    "Verifying HelseID access token alg={} kid={} typ={}",
                    jwt.header.algorithm,
                    jwt.header.keyID,
                    jwt.header.type,
                )
                withContext(Dispatchers.IO) { processor.process(jwt, null) }
            }
            .onFailure {
                log.debug(
                    "HelseID access token rejected: {}: {}",
                    it.javaClass.simpleName,
                    it.message,
                )
            }
            .getOrNull()
}
