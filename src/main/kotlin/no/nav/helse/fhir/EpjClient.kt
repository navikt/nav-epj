package no.nav.helse.fhir

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson3.jackson

fun initEpjClient(baseUrl: String): HttpClient {
  return HttpClient(CIO) {
    install(Logging)
    install(ContentNegotiation) { jackson() }
    defaultRequest { url(baseUrl) }
  }
}
