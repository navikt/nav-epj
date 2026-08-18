package no.nav.helse.utils

import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.di.dependencies
import io.mockk.mockk
import no.nav.helse.core.Environment
import no.nav.helse.core.EpjConfig
import no.nav.helse.core.PostgresConfig
import no.nav.helse.core.SmartConfig
import no.nav.helse.core.ValkeyConfig
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.smart.api.configureSmartRouting
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.valkey.ValkeyService
import org.testcontainers.postgresql.PostgreSQLContainer

private val valkeyService = mockk<ValkeyService>(relaxed = true)
private val encounterService = mockk<EncounterService>(relaxed = true)
private val patientService = mockk<PatientService>(relaxed = true)

fun Application.configureTestSmartDependencies() {
  dependencies {
    provide<Environment>() { simpleTestEnvironment }
    provide<ValkeyService> { valkeyService }
    provide<EncounterService> { encounterService }
    provide<PatientService> { patientService }
  }
  authentication {
    provider("wonderwall-helseid") {
      authenticate { ctx ->
        ctx.principal(HelseIdPrincipal(User(name = "Test", hpr = "111"), DebugInfo("", "")))
      }
    }
  }
  configureSmartRouting()
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
        issuerBaseUrl = "http://test/oidc",
        fhirServerUrl = "http://test/fhir",
        clients =
          listOf(
            SmartClient(
              clientId = "test-client-id",
              redirectUris = listOf("http://test"),
              launchUris = listOf("http://test/fhir/launch"),
            )
          ),
      ),
    valkey = ValkeyConfig("valkey", 8080, false, null, null),
    epj = EpjConfig(baseUrl = "testurl"),
  )

val simpleTestEnvironment =
  Environment(
    postgres = mockk(relaxed = true),
    smart =
      SmartConfig(
        issuerBaseUrl = "http://test/oidc",
        fhirServerUrl = "http://test/fhir",
        clients =
          listOf(
            SmartClient(
              clientId = "test-client-id",
              redirectUris = listOf("http://test"),
              launchUris = listOf("http://test/fhir/launch"),
            )
          ),
      ),
    valkey = ValkeyConfig("valkey", 8080, false, null, null),
    epj = EpjConfig(baseUrl = "testurl"),
  )
