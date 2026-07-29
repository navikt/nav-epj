package no.nav.helse.fhir

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*

fun initEpjClient(): HttpClient {
  return HttpClient(CIO) { install(Logging) }
}
