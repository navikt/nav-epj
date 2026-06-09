package no.nav.helse.utils

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.postgresql.PostgreSQLContainer

abstract class WithPostgresql {
  companion object {
    val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }
    val config = createIntegrationEnvironment(postgres)

    fun runMigrations(clean: Boolean = false) {
      val flyway =
        Flyway.configure()
          .dataSource(config.postgres.url, config.postgres.username, config.postgres.password)
          .locations("db/migration")
          .cleanDisabled(false)
          .load()

      if (clean) {
        flyway.clean()
      }
      flyway.migrate()
    }

    fun connect() {
      Database.connect(
        url = config.postgres.url,
        user = config.postgres.username,
        password = config.postgres.password,
      )
    }
  }
}
