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
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headers
import no.nav.helse.core.Environment
import no.nav.helse.core.utils.logger

class DpopTokenClient(
    private val helseidstsUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient,
    private val env: Environment,
) {
    val logger = logger()

    suspend fun getDpopToken(): String {

        val privateKey: RSAKey = RSAKey.parse(env.persontjensten.privateKey)
        val clientJwk: String = env.persontjensten.clientJwk
        val wellknownUrl: String = env.persontjensten.wellKnownUrl
        val dpopProf= "this is my prof"
        val mybase64encodedjtw = "mybase64encodedjtw"

        val response: TokenResponse =
            httpClient
                .post(helseidstsUrl) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    accept(ContentType.Application.Json)
                    method = HttpMethod.Post
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", clientId)
                                append("client_assertion", mybase64encodedjtw)
                                append(
                                    "client_assertion_type",
                                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                                )
                                append("grant_type", "client_credentials")
                            }
                        )
                    )
                  headers {
                    append("DPoP", dpopProf)
                  }
                }
                .body()
        logger.info("Har hentet accesstoken")

        val access_token = response.access_token
        //  Base64 som en JWT need to decode
        // https://utviklerportal.nhn.no/informasjonstjenester/helseid/bruksmoenstre-og-eksempelkode/bruk-av-helseid/docs/dpop/dpop_no_nbmd
        // Ber om Access Token, legger ved DPoP-bevis med nonce
        // svar: Access Token som inneholder cnf-claim
        // Kaller API, legger ved Access Token (som auth) og DPoP-bevis som header
        return response.access_token
    }
}

data class TokenResponse(val access_token: String, val token_type: String, val expires_in: Int)
