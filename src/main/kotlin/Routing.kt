package no.nav.helse

import com.auth0.jwt.JWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRouting() {
  routing {
    get("/internal/health/alive"){
      call.respondText("alive")
    }
    get("/internal/health/ready"){
      call.respondText("ready")
    }
    singlePageApplication {
      useResources = true
      defaultPage = "index.html"
      filesPath = "dist"
    }
    authenticate("local-stub") {
      get("/login") {
        call.respondRedirect("/callback")
      }

      get("/callback") {
        val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
        call.sessions.set(UserSession(principal?.accessToken.toString()))
        call.respondRedirect("/hello")
      }
    }

    get("/hello") {
      val session = call.sessions.get<UserSession>()

      if (session?.accessToken == null) {
        call.respondRedirect("/login")
        return@get
      }
      val token = session.accessToken.let { JWT.decode(it) }
      call.respondText("Hello, ${token?.subject ?: "unknown"}!")
    }
  }
}
