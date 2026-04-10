package no.nav.helse

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRouting() {
  routing {
    authenticate("auth-oauth-google") {
      get("/login") {
        call.respondRedirect("/callback")
      }

      get("/callback") {
        val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
        call.sessions.set(UserSession(principal?.accessToken.toString()))
        call.respondRedirect("/hello")
      }
    }
    get("/") { // UNAUTHENTICATED (.well-known etc)
      call.respondText("Hello World!")
    }
  }
}
