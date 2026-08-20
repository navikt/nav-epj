package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureCallLogging() {
  install(CallLogging) {
    level = Level.DEBUG
    format { call ->
      val status = call.response.status()
      val httpMethod = call.request.httpMethod.value
      val userAgent = call.request.path()
      "Status: $status, HTTP method: $httpMethod, User-Agent: $userAgent"
    }
  }
}
