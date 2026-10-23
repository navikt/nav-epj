package no.nav.helse.core.db

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.core.Environment
import no.nav.helse.core.PostgresConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabases() {
    val env: Environment by dependencies

    val flyway = getFlyway(env.postgres)

    // flyway.clean()
    flyway.migrate()

    Database.connect(
        url = env.postgres.url,
        user = env.postgres.username,
        password = env.postgres.password,
    )
}

fun getFlyway(postgresConfig: PostgresConfig): Flyway =
    Flyway.configure()
        .dataSource(postgresConfig.url, postgresConfig.username, postgresConfig.password)
        .locations(*flywayLocations(postgresConfig))
        .ignoreMigrationPatterns(*ignoredMigrationPatterns(postgresConfig))
        .cleanDisabled(false)
        .load()

fun ignoredMigrationPatterns(postgresConfig: PostgresConfig): Array<String> =
    if (postgresConfig.seedTestdata) emptyArray() else arrayOf("*:future", "repeatable:missing")

fun flywayLocations(postgresConfig: PostgresConfig): Array<String> =
    if (postgresConfig.seedTestdata) arrayOf("db/migration", "db/testdata")
    else arrayOf("db/migration")
