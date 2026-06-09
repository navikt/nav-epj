package no.nav.helse.core

import io.ktor.server.config.ApplicationConfig

class PostgresConfig(val url: String, val username: String, val password: String)

class Environment(val postgres: PostgresConfig)

fun initEnvironment(config: ApplicationConfig): Environment {
  return Environment(
    postgres =
      PostgresConfig(
        url = config.property("database.url").getString(),
        username = config.property("database.username").getString(),
        password = config.property("database.password").getString(),
      )
  )
}
