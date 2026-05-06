package no.nav.helse.core

import io.ktor.server.config.*

enum class Runtime {
  LOCAL,
  CLOUD,
}

data class OAuthConfig(
  val authorizeUrl: String,
  val accessTokenUrl: String,
  val clientId: String,
  val clientSecret: String,
  val callbackUrl: String,
  val defaultScopes: List<String>,
)

data class Environment(val runtime: Runtime, val oauth: OAuthConfig)

fun initializeEnvironment(config: ApplicationConfig): Environment {
  val runtime =
    if (config.propertyOrNull("ktor.environment")?.getString() == "local") Runtime.LOCAL
    else Runtime.CLOUD
  val oauthConfig = config.config("oauth")
  return Environment(
    runtime = runtime,
    oauth =
      OAuthConfig(
        authorizeUrl = oauthConfig.property("authorizeUrl").getString(),
        accessTokenUrl = oauthConfig.property("accessTokenUrl").getString(),
        clientId = oauthConfig.property("clientId").getString(),
        clientSecret = oauthConfig.property("clientSecret").getString(),
        callbackUrl = oauthConfig.property("callbackUrl").getString(),
        defaultScopes = oauthConfig.property("defaultScopes").getString().split(","),
      ),
  )
}
