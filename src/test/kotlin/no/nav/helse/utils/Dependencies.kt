package no.nav.helse.utils

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.core.Environment
import no.nav.helse.core.PostgresConfig
import no.nav.helse.core.SmartConfig
import org.testcontainers.postgresql.PostgreSQLContainer

fun Application.configureE2E(postgres: PostgreSQLContainer) {
  dependencies { provide<Environment>() { createIntegrationEnvironment(postgres) } }
}

fun createIntegrationEnvironment(postgres: PostgreSQLContainer) =
  Environment(
    postgres =
      PostgresConfig(
        url = "jdbc:${postgres.jdbcUrl.removePrefix("jdbc:")}",
        username = postgres.username,
        password = postgres.password,
      ),
    smart =
      SmartConfig(
        issuerBaseUrl = "http://localhost:8080/oidc",
        fhirServerUrl = "http://localhost:8080/fhir",
      ),
  )
