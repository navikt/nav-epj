package no.nav.helse.epj.persontjensten

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.jackson3.jackson
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.persontjensten.model.PersonName
import no.nav.helse.plugins.uuidModule

private val logger = logger()

class PersontjenstenHttpClient(
  private val baseUrl: String,
) {
  val httpClient = HttpClient {
    install(ContentNegotiation) {
      jackson {
        addModule(uuidModule)
      }
    }
    install(HttpRequestRetry) {
      retryOnServerErrors(maxRetries = 5)
    }
  }

  suspend fun getByNin(fnr: String): PersonName? {
    try {
      val respone = httpClient.post("$baseUrl/full-access/person/get-by-nin") {
        parameters {
          append("informationParts", "Name")
          append("includeHistory", "false")
        }
        contentType(ContentType.Application.FormUrlEncoded)
        accept(ContentType.Application.Json)
        setBody("nin=$fnr")
        /* TODO lege til støtte for DPoP
        headers {
          append("Authorization", "DPoP $token")
        }
         */
      }.body<PersonName>()

      return respone

    } catch (exception: Exception) {
      logger.error(exception.message ?: "Feil i persontjensten")
    }

    return null
  }

}
