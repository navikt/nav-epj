package no.nav.helse.core

import io.ktor.server.config.*
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.parseRegisteredScopes

class Environment(
  val postgres: PostgresConfig,
  val smart: SmartConfig,
  val valkey: ValkeyConfig,
  val epj: EpjConfig,
)

data class PostgresConfig(val url: String, val username: String, val password: String)

data class EpjConfig(val baseUrl: String)

class SmartConfig(
  val issuerBaseUrl: String,
  val fhirServerUrl: String,
  val clients: List<SmartClient>,
)

private fun smartClient(c: ApplicationConfig): SmartClient {
  val clientId = c.property("clientId").getString()
  val clientSecret = c.propertyOrNull("clientSecret")?.getString()
  val jwksUri = c.propertyOrNull("jwksUri")?.getString()
  val method =
    c.propertyOrNull("tokenEndpointAuthMethod")?.getString()?.let(TokenEndpointAuthMethod::from)
      ?: if (clientSecret != null) TokenEndpointAuthMethod.CLIENT_SECRET_BASIC
      else TokenEndpointAuthMethod.NONE

  when (method) {
    TokenEndpointAuthMethod.PRIVATE_KEY_JWT ->
      require(jwksUri != null) {
        "smart.clients: client '${c.property("clientId").getString()}' declares private_key_jwt but has no jwksUri"
      }
    TokenEndpointAuthMethod.CLIENT_SECRET_BASIC ->
      require(clientSecret != null) {
        "smart.clients: client '${c.property("clientId").getString()}' declares client_secret_basic but has no clientSecret"
      }
    TokenEndpointAuthMethod.NONE -> Unit
  }

  return SmartClient(
    clientId = clientId,
    redirectUris = c.property("redirectUris").getList(),
    launchUris = c.property("launchUris").getList(),
    tokenEndpointAuthMethod = method,
    clientSecret = clientSecret,
    jwksUri = jwksUri,
    allowedScopes = parseRegisteredScopes(c.property("scopes").getList()),
  )
}

data class ValkeyConfig(
  val host: String,
  val port: Int,
  val useTLS: Boolean,
  val username: String?,
  val password: String?,
)

fun initEnvironment(config: ApplicationConfig): Environment {
  return Environment(
    postgres =
      PostgresConfig(
        url = config.property("database.url").getString(),
        username = config.property("database.user").getString(),
        password = config.property("database.password").getString(),
      ),
    smart =
      SmartConfig(
        issuerBaseUrl = config.property("smart.issuerBaseUrl").getString(),
        fhirServerUrl = config.property("smart.fhirServerUrl").getString(),
        clients = config.configList("smart.clients").map { c -> smartClient(c) },
      ),
    valkey =
      ValkeyConfig(
        host = config.property("valkey.host").getString(),
        port = config.property("valkey.port").getString().toInt(),
        useTLS = config.property("valkey.useTLS").getString().toBoolean(),
        username = config.propertyOrNull("valkey.username")?.getString(),
        password = config.propertyOrNull("valkey.password")?.getString(),
      ),
    epj = EpjConfig(baseUrl = config.property("epj.baseUrl").getString()),
  )
}
