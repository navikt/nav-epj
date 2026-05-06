package no.nav.helse

import com.auth0.jwt.JWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.p
import no.nav.helse.auth.UserSession
import no.nav.helse.auth.getSession

fun Application.configureRouting() {
  routing {
    authenticate("local-stub") {
      get("/login") {}

      get("/callback") {
        val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
        principal?.state?.let { state ->
          call.sessions.set(UserSession(state, principal.accessToken))
        }
        call.respondRedirect("/home")
      }
    }

    get("/internal/health/alive") { call.respondText("alive") }
    get("/internal/health/ready") { call.respondText("ready") }

    singlePageApplication {
      useResources = true
      defaultPage = "index.html"
      filesPath = "dist"
    }

    get("/") { call.respondHtml { body { p { a("/login") { +"Login" } } } } }

    get("/home") {
      val userSession: UserSession? = getSession(call)
      val token = userSession?.accessToken.let { JWT.decode(it) }
      if (userSession != null) {
        call.respondText("Welcome ${token.subject}!")
      }
    }

    get("/login-after-fallback") { call.respondText("Redirected after fallback") }
  }
}
