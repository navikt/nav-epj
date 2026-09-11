package no.nav.helse.helseId

import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headers
import java.util.Base64
import no.nav.helse.core.Environment
import no.nav.helse.core.utils.logger
import tools.jackson.module.kotlin.jacksonObjectMapper

class DpopTokenClient(private val httpClient: HttpClient, private val env: Environment) {
    val logger = logger()

    // This is the doc
    // https://utviklerportal.nhn.no/informasjonstjenester/helseid/bruksmoenstre-og-eksempelkode/bruk-av-helseid/docs/dpop/dpop_no_nbmd

    suspend fun getDpopProfAndAccesTokenToken(): DpopPRofAndAccessToken {
        val helseidTokenUrl: String = env.dpop.helseidTokenAuthUrl
        val helseidClientJWT: RSAKey = RSAKey.parse(env.dpop.clientJwk)
        val clientId: String = env.dpop.clientId

        val base64EncodeRSAKey =
            Base64.getEncoder()
                .encodeToString(
                    jacksonObjectMapper().writeValueAsBytes(helseidClientJWT.toString())
                )

        val response =
            httpClient.post(helseidTokenUrl) {
                contentType(ContentType.Application.FormUrlEncoded)
                accept(ContentType.Application.Json)
                method = HttpMethod.Post
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", clientId)
                            append("client_assertion", base64EncodeRSAKey)
                            append(
                                "client_assertion_type",
                                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            )
                            append("grant_type", "client_credentials")
                        }
                    )
                )
                headers { append("DPoP", base64EncodeRSAKey) }
            }

        if (response.status == HttpStatusCode.BadRequest) {
            val dpopNonceResponse = response.body<DpopNonceResponse>()
            if (dpopNonceResponse.error.equals("use_dpop_nonce")) {

                val dpopNonceHeader = response.headers["DPoP-Nonce"]!!

                // get private key
                // get public key
                // add nonce
                // create a RSAKey
                // base64EncodeRSAKeyWithNonce

                val base64EncodeRSAKeyWithNonce =
                    Base64.getEncoder()
                        .encodeToString(
                            jacksonObjectMapper().writeValueAsBytes(helseidClientJWT.toString())
                        )

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
                                        append("client_assertion", base64EncodeRSAKey)
                                        append(
                                            "client_assertion_type",
                                            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                                        )
                                        append("grant_type", "client_credentials")
                                    }
                                )
                            )
                            headers { append("DPoP", dpopNonceHeader) }
                        }
                        .body<DpopResponse>()

                val dpopPRofAndAccessToken =
                    DpopPRofAndAccessToken(
                        access_token = dpopResponse.access_token,
                        dpopProf = base64EncodeRSAKeyWithNonce,
                    )
                return dpopPRofAndAccessToken
            }
        }
        throw RuntimeException("Faild to get DpopProf snd AccesTokenToken")
    }
}

data class DpopNonceResponse(val error: String, val error_description: String)

data class DpopResponse(val access_token: String, val token_type: String, val expires_in: String)

data class DpopPRofAndAccessToken(val access_token: String, val dpopProf: String)
