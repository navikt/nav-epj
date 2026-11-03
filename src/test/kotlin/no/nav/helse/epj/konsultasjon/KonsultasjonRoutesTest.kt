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
import no.nav.helse.epj.clinic.Clinic
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.konsultasjon.routes.konsultasjonRoutes
import no.nav.helse.epj.patient.ActivePatientService
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.patient.PatientService
import no.nav.helse.epj.sessionorg.SessionOrgResolution
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class KonsultasjonRoutesTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val valkeyService = mockk<ValkeyService>(relaxed = true)
    private val patientService = mockk<PatientService>()
    private val sessionOrgResolver =
        mockk<SessionOrgResolver> {
            coEvery { resolve(any()) } returns
                SessionOrgResolution.Resolved(
                    Clinic(ClinicId(Uuid.generateV4()), "Fjordvik", DEFAULT_ORG_NUMBER, null),
                    derived = true,
                )
        }

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
                        ActivePatientService(patientService, valkeyService, sessionOrgResolver),
                        sessionOrgResolver,
                    )
                }
            }
        }
        client.block()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun konsultasjon(
        id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
    ) =
        Konsultasjon(
            id = id,
            patientId = patientId,
            hpr = emptyList(),
            clinicId = ClinicId(Uuid.generateV4()),
            journalnotat = emptyList(),
            diagnoser = emptyList(),
            startetTidspunkt = LocalDateTime.now().minusDays(1),
            avsluttetTidspunkt = null,
            status = KonsultasjonStatus.PAAGAAENDE,
            problemstilling = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjon with known id returns 200`() = testApp {
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId, DEFAULT_ORG_NUMBER) } returns
            konsultasjon(id = konsultasjonId)

        val response = get("/api/konsultasjon/${konsultasjonId.value}")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjon with unknown id returns 404`() = testApp {
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId, DEFAULT_ORG_NUMBER) } throws
            KonsultasjonNotFoundException(konsultasjonId)

        val response = get("/api/konsultasjon/${konsultasjonId.value}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjoner for unknown patient returns 404`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjoner(patientId, DEFAULT_ORG_NUMBER) } throws
            KonsultasjonNotFoundForPatientException(patientId)

        val response = get("/api/patients/${patientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET konsultasjoner does not change the active patient`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { konsultasjonService.getKonsultasjoner(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(konsultasjon(patientId = patientId))

        val response = get("/api/patients/${patientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner for a listed patient starts it and makes it active`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { patientService.isListedAt(patientId, DEFAULT_ORG_NUMBER) } returns true
        coEvery { valkeyService.getActivePatientWithExpiry("111", DEFAULT_ORG_NUMBER) } returns
            ActivePatient(patientId.value.toString(), Instant.now().plusSeconds(60))
        coEvery {
            konsultasjonService.getOrCreateKonsultasjon(patientId, any(), DEFAULT_ORG_NUMBER)
        } returns konsultasjon(patientId = patientId)

        val response = post("/api/patients/${patientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) {
            valkeyService.setActivePatient("111", DEFAULT_ORG_NUMBER, patientId.value.toString())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner for a patient not on a list at the session org returns 404 and changes nothing`() =
        testApp {
            val patientId = PatientId(Uuid.generateV4())
            coEvery { patientService.isListedAt(patientId, DEFAULT_ORG_NUMBER) } returns false

            val response = post("/api/patients/${patientId.value}/konsultasjoner")

            assertEquals(HttpStatusCode.NotFound, response.status)
            coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
            coVerify(exactly = 0) {
                konsultasjonService.getOrCreateKonsultasjon(any(), any(), any())
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner without a session org returns 409 and changes nothing`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { sessionOrgResolver.resolve("111") } returns SessionOrgResolution.NeedsChoice

        val response = post("/api/patients/${patientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.Conflict, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
        coVerify(exactly = 0) { konsultasjonService.getOrCreateKonsultasjon(any(), any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST konsultasjoner for an unknown patient returns 404 and changes nothing`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { patientService.isListedAt(patientId, DEFAULT_ORG_NUMBER) } returns false

        val response = post("/api/patients/${patientId.value}/konsultasjoner")

        assertEquals(HttpStatusCode.NotFound, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
        coVerify(exactly = 0) { konsultasjonService.getOrCreateKonsultasjon(any(), any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST avbryt cancels the konsultasjon and returns 200`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery {
            konsultasjonService.cancelKonsultasjon(konsultasjonId, patientId, DEFAULT_ORG_NUMBER)
        } returns Unit

        val response =
            post("/api/patients/${patientId.value}/konsultasjoner/${konsultasjonId.value}/avbryt")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) {
            konsultasjonService.cancelKonsultasjon(konsultasjonId, patientId, DEFAULT_ORG_NUMBER)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST avbryt for a konsultasjon not owned by the patient returns 404`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery {
            konsultasjonService.cancelKonsultasjon(konsultasjonId, patientId, DEFAULT_ORG_NUMBER)
        } throws KonsultasjonNotFoundForPatientException(patientId)

        val response =
            post("/api/patients/${patientId.value}/konsultasjoner/${konsultasjonId.value}/avbryt")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
