package database

import io.ktor.server.application.*
import no.nav.helse.core.db.DatabaseConnection
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

fun Application.configureDatabase() {
  val config = environment.config
  val host = config.property("database.host").getString()
  val port = config.property("database.port").getString()
  val name = config.property("database.name").getString()
  val username = config.property("database.username").getString()
  val password = config.property("database.password").getString()
  val isLocal = config.propertyOrNull("ktor.environment")?.getString() == "local"

  // Run Flyway migrations using JDBC
  val flywayConfig =
    Flyway.configure().dataSource("jdbc:postgresql://$host:$port/$name", username, password)

  if (isLocal) {
    flywayConfig.locations("classpath:db/migration", "classpath:db/migration/local")
  }

  flywayConfig.load().migrate()

  DatabaseConnection.database =
    R2dbcDatabase.connect(
      url = "r2dbc:postgresql://$host:$port/$name",
      user = username,
      password = password,
    )
}
