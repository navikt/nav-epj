package no.nav.helse

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import kotlinx.serialization.Serializable

fun Application.configureSecurity() {
  val config = environment.config.config("oauth")

  authentication {
    oauth("local-stub") {
      urlProvider = { config.property("callbackUrl").getString() }
      providerLookup = {
        OAuthServerSettings.OAuth2ServerSettings(
          name = "local-stub",
          authorizeUrl = config.property("authorizeUrl").getString(),
          accessTokenUrl = config.property("accessTokenUrl").getString(),
          requestMethod = HttpMethod.Post,
          clientId = config.property("clientId").getString(),
          clientSecret = config.property("clientSecret").getString(),
          defaultScopes = config.property("defaultScopes").getString().split(","),
        )
      }
      // TODO fallback = {}
      client = HttpClient(CIO)
    }
  }
}

@Serializable
class UserSession(val accessToken: String)
