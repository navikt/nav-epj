package no.nav.tsm.plugins

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database


fun Application.configureDatabases() {
    val url = environment.config.property("database.url").getString()
    val user = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    val flyway = Flyway.configure()
        .dataSource(url, user, password)
        .locations("db/migrations")
        .load()

    flyway.migrate()

    Database.connect(url, user = user, password = password)
}
