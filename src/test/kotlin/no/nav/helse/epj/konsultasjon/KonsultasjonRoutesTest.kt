package no.nav.helse.epj.konsultasjon

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.routes.konsultasjonRoutes
import no.nav.helse.epj.pasient.PasientId
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
      install(ContentNegotiation) { jackson() }
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
    pasientId: PasientId = PasientId(Uuid.generateV4()),
  ) =
    Konsultasjon(
      id = id,
      pasientId = pasientId,
      hpr = emptyList(),
      journalnotat = emptyList(),
      diagnoser = emptyList(),
      startetTidspunkt = LocalDateTime.now().minusDays(1),
      avsluttetTidspunkt = null,
      status = KonsultasjonStatus.PÅGÅENDE,
      problemstilling = null,
    )

  @Test
  fun `GET konsultasjon with known id returns 200`() = testApp {
    val konsultasjonId = KonsultasjonId(Uuid.generateV4())
    coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
      konsultasjon(id = konsultasjonId)

    val response = get("/api/konsultasjon/${konsultasjonId.value}")

    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun `GET konsultasjon with unknown id returns 404`() = testApp {
    val konsultasjonId = KonsultasjonId(Uuid.generateV4())
    coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } throws
      KonsultasjonNotFoundException(konsultasjonId)

    val response = get("/api/konsultasjon/${konsultasjonId.value}")

    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `GET konsultasjoner for unknown patient returns 404`() = testApp {
    val pasientId = PasientId(Uuid.generateV4())
    coEvery { konsultasjonService.getKonsultasjoner(pasientId) } throws
      KonsultasjonNotFoundForPatientException(pasientId)

    val response = get("/api/patients/${pasientId.value}/konsultasjoner")

    assertEquals(HttpStatusCode.NotFound, response.status)
  }
}
