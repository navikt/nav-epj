package no.nav.helse.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
  install(CORS) {
    allowHost("localhost:5173")
    allowHost("localhost:5174")
    allowHost("localhost:3000")
    allowHost("epj.dev.nav.no")
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowHeader("X-Wonderwall-Id-Token")
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Patch)
    allowMethod(HttpMethod.Delete)
  }
}
