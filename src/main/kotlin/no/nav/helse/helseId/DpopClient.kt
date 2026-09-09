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
                                append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",)
                                append("grant_type", "client_credentials")
                            }
                        )
                    )
                }
                .body()
        logger.info("Har hentet accesstoken")

        return response.access_token
    }
}

data class TokenResponse(val access_token: String, val token_type: String, val expires_in: Int)
