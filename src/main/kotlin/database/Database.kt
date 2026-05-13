package database

import io.ktor.server.application.*
import no.nav.helse.core.db.DatabaseConnection
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabase() {
  val config = environment.config
  val url = config.property("database.url").getString()
  val username = config.property("database.username").getString()
  val password = config.property("database.password").getString()

  Flyway.configure()
    .dataSource(url, username, password)
    .locations("classpath:db/migration")
    .load()
    .migrate()

  DatabaseConnection.database =
    Database.connect(
      url = url,
      driver = "org.postgresql.Driver",
      user = username,
      password = password,
    )
}
