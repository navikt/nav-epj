package no.nav.helse.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json


fun Application.configureSerialization() {
  routing {
    install(ContentNegotiation) {
      json(
        Json { ignoreUnknownKeys = true }
      )
    }
  }
}

