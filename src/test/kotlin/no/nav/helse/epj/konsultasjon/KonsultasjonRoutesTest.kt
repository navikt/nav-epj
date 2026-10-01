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
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.routes.konsultasjonRoutes
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.pasient.ActivePatientService
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService
import org.junit.Test

class KonsultasjonRoutesTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val valkeyService = mockk<ValkeyService>(relaxed = true)
    private val pasientService = mockk<PasientService>()

    private fun testApp(block: suspend io.ktor.client.HttpClient.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { jackson() }
            configureStatusPages()
            authentication {
                provider("wonderwall-helseid") {
                    authenticate { ctx ->
                        ctx.principal(
                            HelseIdPrincipal(User(name = "Test", hpr = "111"), DebugInfo("", ""))
                        )
                    }
                }
            }
            routing {
                authenticate("wonderwall-helseid") {
                    konsultasjonRoutes(
                        konsultasjonService,
                        ActivePatientService(pasientService, valkeyService),
                    )
                }
            }
        }
        client.block()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun konsultasjon(
        id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId: PasientId = PasientId(Uuid.generateV4()),
    ) =
        Konsultasjon(
            id = id,
            pasientId = pasientId,
            legekontorId = LegekontorId(Uuid.generateV4()),
            hpr = emptyList(),
            journalnotat = emptyList(),
            diagnoser = emptyList(),
            startetTidspunkt = LocalDateTime.now().minusDays(1),
            avsluttetTidspunkt = null,
            status = KonsultasjonStatus.PÅGÅENDE,
            problemstilling = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjon with known id returns 200`() = testApp {
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
            konsultasjon(id = konsultasjonId)

        val response = get("/api/konsultasjon/${konsultasjonId.value}")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjon with unknown id returns 404`() = testApp {
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } throws
            KonsultasjonNotFoundException(konsultasjonId)

        val response = get("/api/konsultasjon/${konsultasjonId.value}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjoner for unknown patient returns 404`() = testApp {
        val pasientId = PasientId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjoner(pasientId) } throws
            KonsultasjonNotFoundForPatientException(pasientId)

        val response = get("/api/patients/${pasientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjoner does not change the active patient`() = testApp {
        val pasientId = PasientId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjoner(pasientId) } returns
            listOf(konsultasjon(pasientId = pasientId))

        val response = get("/api/patients/${pasientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun linkedPasient(pasientId: PasientId, hprs: List<String>) =
        Pasient(
            id = pasientId,
            legekontorId = LegekontorId(Uuid.generateV4()),
            hprNumbers = hprs.map { HelsepersonellHpr(it) },
            fornavn = "Ola",
            etternavn = "Nordmann",
            personident = "01019012345",
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner for a linked patient starts it and makes it active`() = testApp {
        val pasientId = PasientId(Uuid.generateV4())
        coEvery { pasientService.getPasientById(pasientId) } returns
            linkedPasient(pasientId, listOf("111"))
        coEvery { valkeyService.getActivePatientWithExpiry("111") } returns
            ActivePatient(pasientId.value.toString(), Instant.now().plusSeconds(60))
        coEvery { konsultasjonService.getOrCreateKonsultasjon(pasientId, any()) } returns
            konsultasjon(pasientId = pasientId)

        val response = post("/api/patients/${pasientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { valkeyService.setActivePatient("111", pasientId.value.toString()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner for a patient not linked to the clinician returns 404 and changes nothing`() =
        testApp {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { pasientService.getPasientById(pasientId) } returns
                linkedPasient(pasientId, listOf("999"))

            val response = post("/api/patients/${pasientId.value}/konsultasjoner")

            assertEquals(HttpStatusCode.NotFound, response.status)
            coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any()) }
            coVerify(exactly = 0) { konsultasjonService.getOrCreateKonsultasjon(any(), any()) }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner for an unknown patient returns 404 and changes nothing`() = testApp {
        val pasientId = PasientId(Uuid.generateV4())
        coEvery { pasientService.getPasientById(pasientId) } returns null

        val response = post("/api/patients/${pasientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.NotFound, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any()) }
        coVerify(exactly = 0) { konsultasjonService.getOrCreateKonsultasjon(any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST avbryt cancels the konsultasjon and returns 200`() = testApp {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.cancelKonsultasjon(konsultasjonId, pasientId) } returns Unit

        val response =
            post("/api/patients/${pasientId.value}/konsultasjoner/${konsultasjonId.value}/avbryt")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { konsultasjonService.cancelKonsultasjon(konsultasjonId, pasientId) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST avbryt for a konsultasjon not owned by the patient returns 404`() = testApp {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.cancelKonsultasjon(konsultasjonId, pasientId) } throws
            KonsultasjonNotFoundForPatientException(pasientId)

        val response =
            post("/api/patients/${pasientId.value}/konsultasjoner/${konsultasjonId.value}/avbryt")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
