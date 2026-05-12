package database

import io.ktor.server.application.*
import no.nav.helse.core.db.DatabaseConnection
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabase() {
  val config = environment.config
  val host = config.property("database.host").getString()
  val port = config.property("database.port").getString()
  val name = config.property("database.name").getString()
  val username = config.property("database.username").getString()
  val password = config.property("database.password").getString()

  Flyway.configure()
    .dataSource("jdbc:postgresql://$host:$port/$name", username, password)
    .locations("classpath:db/migration")
    .load()
    .migrate()

  DatabaseConnection.database =
    Database.connect(
      url = "jdbc:postgresql://$host:$port/$name",
      driver = "org.postgresql.Driver",
      user = username,
      password = password,
    )
}
