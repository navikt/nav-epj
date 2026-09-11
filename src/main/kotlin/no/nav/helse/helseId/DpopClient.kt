package no.nav.helse.helseId

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headers
import java.time.Instant
import java.util.Date
import java.util.UUID
import no.nav.helse.core.Environment
import no.nav.helse.core.utils.logger

class DpopClient(private val httpClient: HttpClient, private val env: Environment) {
    val logger = logger()

    // https://utviklerportal.nhn.no/informasjonstjenester/helseid/bruksmoenstre-og-eksempelkode/bruk-av-helseid/docs/dpop/dpop_no_nbmd

    suspend fun getDpopProfAndAccesToken(): DpopPRofAndAccessToken {
        val helseidTokenUrl: String = env.dpop.helseidTokenAuthUrl
        val clientId: String = env.dpop.clientId
        val clientAssertion = createClientAssertion(env.dpop.clientJwk, helseidTokenUrl, clientId)

        val response =
            httpClient.post(helseidTokenUrl) {
                contentType(ContentType.Application.FormUrlEncoded)
                accept(ContentType.Application.Json)
                method = HttpMethod.Post
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("client_assertion", clientAssertion)
                            append(
                                "client_assertion_type",
                                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            )
                            append("grant_type", "client_credentials")
                        }
                    )
                )
                headers { append("DPoP", createDpopProof(env.dpop.clientJwk, helseidTokenUrl)) }
            }

        logger.info("First response http: " + response.status.toString())

        if (response.status == HttpStatusCode.BadRequest) {
            logger.info("Response body is" + response.bodyAsText())

            val dpopNonceResponse = response.body<DpopNonceResponse>()
            if (dpopNonceResponse.error.equals("use_dpop_nonce")) {
                val dpopNonceHeader = response.headers["DPoP-Nonce"]!!
                val dpopResponse =
                    httpClient
                        .post(helseidTokenUrl) {
                            contentType(ContentType.Application.FormUrlEncoded)
                            accept(ContentType.Application.Json)
                            method = HttpMethod.Post
                            setBody(
                                FormDataContent(
                                    Parameters.build {
                                        append("client_id", clientId)
                                        append("client_assertion", clientAssertion)
                                        append(
                                            "client_assertion_type",
                                            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                                        )
                                        append("grant_type", "client_credentials")
                                    }
                                )
                            )
                            headers {
                                append(
                                    "DPoP",
                                    createDpopProof(
                                        env.dpop.clientJwk,
                                        helseidTokenUrl,
                                        dpopNonceHeader,
                                    ),
                                )
                            }
                        }
                        .body<DpopResponse>()

                return DpopPRofAndAccessToken(
                    access_token = dpopResponse.access_token,
                    dpopProf = createDpopProof(env.dpop.clientJwk, helseidTokenUrl, dpopNonceHeader),
                )
            }
        }
        throw RuntimeException("Faild to get DpopProf and AccesTokenToken")
    }
}

internal fun createClientAssertion(clientJwk: String, tokenUrl: String, clientId: String): String {
    val helseidClientJWT: RSAKey = RSAKey.parse(clientJwk)
    val now = Instant.now()
    val claims =
        JWTClaimsSet.Builder()
            .issuer(clientId)
            .subject(clientId)
            .audience(tokenUrl)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .jwtID(UUID.randomUUID().toString())
            .build()

    val header =
        JWSHeader.Builder(JWSAlgorithm.parse(helseidClientJWT.algorithm.name))
            .keyID(helseidClientJWT.keyID)
            .type(JOSEObjectType("JWT"))
            .build()

    return SignedJWT(header, claims)
        .apply { sign(RSASSASigner(helseidClientJWT.toPrivateKey())) }
        .serialize()
}

internal fun createDpopProof(clientJwk: String, tokenUrl: String, nonce: String? = null): String {
    val helseidClientJWT: RSAKey = RSAKey.parse(clientJwk)
    val now = Instant.now()
    val claimsBuilder =
        JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .claim("htm", HttpMethod.Post.value)
            .claim("htu", tokenUrl)

    if (!nonce.isNullOrBlank()) {
        claimsBuilder.claim("nonce", nonce)
    }

    val header =
        JWSHeader.Builder(JWSAlgorithm.parse(helseidClientJWT.algorithm.name))
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(helseidClientJWT.toPublicJWK())
            .build()

    return SignedJWT(header, claimsBuilder.build())
        .apply { sign(RSASSASigner(helseidClientJWT.toPrivateKey())) }
        .serialize()
}

data class DpopNonceResponse(val error: String, val error_description: String)

data class DpopResponse(val access_token: String, val token_type: String, val expires_in: String)

data class DpopPRofAndAccessToken(val access_token: String, val dpopProf: String)
