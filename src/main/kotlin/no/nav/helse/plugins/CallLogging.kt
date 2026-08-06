package no.nav.helse.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.authorization
import io.ktor.server.request.httpMethod
import org.slf4j.event.Level

fun Application.configureCallLogging() {
  install(CallLogging) {
    level = Level.INFO
    format { call ->
      val status = call.response.status()
      val httpMethod = call.request.httpMethod.value
      val userAgent = call.request.headers["User-Agent"]
      val authorization = call.request.headers["Authorization"]
      "Status: $status, HTTP method: $httpMethod, User-Agent: $userAgent, Authorization: $authorization"
    }
  }
}
