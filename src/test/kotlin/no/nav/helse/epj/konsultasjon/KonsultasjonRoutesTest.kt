package no.nav.helse.epj.konsultasjon

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.routes.konsultasjonRoutes
import no.nav.helse.epj.pasient.PatientId
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.valkey.ValkeyService
import org.junit.Test

class KonsultasjonRoutesTest {

  private val konsultasjonService = mockk<KonsultasjonService>()
  private val valkeyService = mockk<ValkeyService>(relaxed = true)

  private fun testApp(block: suspend io.ktor.client.HttpClient.() -> Unit) = testApplication {
    application {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      configureStatusPages()
      authentication {
        provider("wonderwall-helseid") {
          authenticate { ctx ->
            ctx.principal(HelseIdPrincipal(User(name = "Test", hpr = "111"), DebugInfo("", "")))
          }
        }
      }
      routing {
        authenticate("wonderwall-helseid") {
          konsultasjonRoutes(konsultasjonService, valkeyService)
        }
      }
    }
    client.block()
  }

  private fun konsultasjon(
    id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
    pasientId: PatientId = PatientId(Uuid.generateV4()),
  ) =
    Konsultasjon(
      id = id,
      pasientId = pasientId,
      hpr = emptyList(),
      journalnotat = emptyList(),
      diagnoser = emptyList(),
      startetTidspunkt = LocalDateTime(2024, 1, 1, 0, 0),
      avsluttetTidspunkt = null,
      status = KonsultasjonStatus.PÅGÅENDE,
      problemstilling = null,
    )

  @Test
  fun `GET konsultasjon med kjent id returnerer 200`() = testApp {
    val konsultasjonId = KonsultasjonId(Uuid.generateV4())
    coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
      konsultasjon(id = konsultasjonId)

    val response = get("/api/konsultasjon/${konsultasjonId.value}")

    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun `GET konsultasjon med ukjent id returnerer 500`() = testApp {
    val konsultasjonId = KonsultasjonId(Uuid.generateV4())
    coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } throws
      KonsultasjonNotFoundException(konsultasjonId)

    val response = get("/api/konsultasjon/${konsultasjonId.value}")

    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `test`() = testApp {
    val patientId = PatientId(Uuid.generateV4())
    coEvery { konsultasjonService.getKonsultasjoner(patientId) } throws
      KonsultasjonNotFoundForPatientException(patientId)

    val response = get("/api/patients/${patientId.value}/konsultasjoner")

    assertEquals(HttpStatusCode.NotFound, response.status)
  }
}
