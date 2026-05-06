package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import no.nav.helse.auth.UserSession

fun Application.configureSession() {
  install(Sessions) {
    cookie<UserSession>("user_session")
  }
}
