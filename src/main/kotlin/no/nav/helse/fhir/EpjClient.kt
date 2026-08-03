package no.nav.helse.fhir

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun initEpjClient(baseUrl: String): HttpClient {
  return HttpClient(CIO) {
    install(Logging)
    install(ContentNegotiation) {
      json(
        Json {
          prettyPrint = true
          isLenient = true
          ignoreUnknownKeys = true
        }
      )
    }
    defaultRequest { url(baseUrl) }
  }
}
