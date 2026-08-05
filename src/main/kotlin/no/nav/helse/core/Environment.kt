package no.nav.helse.core

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.server.config.*
import no.nav.helse.smart.security.SmartClient

class Environment(
  val postgres: PostgresConfig,
  val smart: SmartConfig,
  val valkey: ValkeyConfig,
  val httpClient: HttpClient,
  val epj: EpjConfig,
)

data class PostgresConfig(val url: String, val username: String, val password: String)

data class EpjConfig(val baseUrl: String)

class SmartConfig(
  val issuerBaseUrl: String,
  val fhirServerUrl: String,
  val clients: List<SmartClient>,
)

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
        clients =
          config.configList("smart.clients").map { c ->
            SmartClient(
              clientId = c.property("clientId").getString(),
              redirectUris = c.property("redirectUris").getList(),
              launchUris = c.property("launchUris").getList(),
              clientSecret = c.propertyOrNull("clientSecret")?.getString(),
            )
          },
      ),
    valkey =
      ValkeyConfig(
        host = config.property("valkey.host").getString(),
        port = config.property("valkey.port").getString().toInt(),
        useTLS = config.property("valkey.useTLS").getString().toBoolean(),
        username = config.propertyOrNull("valkey.username")?.getString(),
        password = config.propertyOrNull("valkey.password")?.getString(),
      ),
    httpClient = HttpClient(CIO) { install(Logging) },
    epj = EpjConfig(baseUrl = config.property("epj.baseUrl").getString()),
  )
}
