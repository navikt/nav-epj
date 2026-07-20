package no.nav.helse.core

import io.ktor.server.config.ApplicationConfig
import no.nav.helse.smart.security.SmartClient

class Environment(val postgres: PostgresConfig, val smart: SmartConfig, val valkey: ValkeyConfig) {}

data class PostgresConfig(val url: String, val username: String, val password: String)

class SmartConfig(
  val issuerBaseUrl: String,
  val fhirServerUrl: String,
  val clients: List<SmartClient>,
)

data class ValkeyConfig(val host: String, val port: Int)

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
              c.propertyOrNull("clientSecret")?.getString(),
            )
          },
      ),
    valkey =
      ValkeyConfig(
        host = config.property("valkey.host").getString(),
        port = config.property("valkey.port").getString().toInt(),
      ),
  )
}
