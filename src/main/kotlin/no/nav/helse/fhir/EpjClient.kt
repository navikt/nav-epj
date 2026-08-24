package no.nav.helse.fhir

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson3.*
import no.nav.helse.plugins.uuidModule
import tools.jackson.databind.SerializationFeature
import tools.jackson.module.kotlin.KotlinModule

fun initEpjClient(baseUrl: String): HttpClient {
  return HttpClient(CIO) {
    install(Logging)
    install(ContentNegotiation) {
      jackson {
        enable(SerializationFeature.INDENT_OUTPUT)
        addModule(KotlinModule.Builder().build())
        addModule(uuidModule)
      }
    }
    defaultRequest { url(baseUrl) }
  }
}
