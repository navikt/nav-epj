package no.nav.helse.core.db

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.core.Environment
import no.nav.helse.core.PostgresConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabases() {
  val env: Environment by dependencies

  val flyway = getFlyway(env.postgres)

  flyway.clean()
  flyway.migrate()

  DatabaseFactory.init(env.postgres.url, env.postgres.username, env.postgres.password)
}

fun getFlyway(postgresConfig: PostgresConfig): Flyway =
  Flyway.configure()
    .dataSource(postgresConfig.url, postgresConfig.username, postgresConfig.password)
    .locations("db/migration")
    .cleanDisabled(false)
    .load()

object DatabaseFactory {
  fun init(url: String, user: String, password: String) {
    val database = Database.connect(url, user = user, password = password)

    // Initialize schema
    transaction(database) {
      SchemaUtils.create(PasientTable)
      SchemaUtils.create(LegekontorTable)
      SchemaUtils.create(HelsepersonellTable)
      SchemaUtils.create(KonsultasjonTable)
      SchemaUtils.create(DiagnoseTable)
    }
  }
}
