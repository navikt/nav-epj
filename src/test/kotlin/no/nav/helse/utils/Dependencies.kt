package no.nav.helse.utils

import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.di.dependencies
import io.mockk.mockk
import no.nav.helse.core.Environment
import no.nav.helse.core.PostgresConfig
import no.nav.helse.core.SmartConfig
import no.nav.helse.epj.ClinicianContextStore
import no.nav.helse.fhir.FhirService
import no.nav.helse.helseIdAuth.DebugInfo
import no.nav.helse.helseIdAuth.HelseIdPrincipal
import no.nav.helse.helseIdAuth.User
import no.nav.helse.smart.api.configureSmartRouting
import no.nav.helse.smart.db.AuthCodeContext
import no.nav.helse.smart.db.LaunchContext
import no.nav.helse.smart.db.SingleUseStore
import no.nav.helse.smart.db.SmartClient
import org.testcontainers.postgresql.PostgreSQLContainer

private val fhirService = mockk<FhirService>()
private val launchStore = mockk<SingleUseStore<LaunchContext>>(relaxed = true)
private val authCodesStore = mockk<SingleUseStore<AuthCodeContext>>(relaxed = true)
private val clinicianContextStore = mockk<ClinicianContextStore>()

fun Application.configureTestSmartDependencies() {
  dependencies {
    provide<Environment>() { simpleTestEnvironment }
    provide<FhirService> { fhirService }
    provide<SingleUseStore<LaunchContext>> { launchStore }
    provide<SingleUseStore<AuthCodeContext>> { authCodesStore }
    provide<ClinicianContextStore> { clinicianContextStore }
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
  )
