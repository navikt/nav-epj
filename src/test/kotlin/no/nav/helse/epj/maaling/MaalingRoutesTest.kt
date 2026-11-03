package no.nav.helse.epj.maaling

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.clinic.Clinic
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.sessionorg.SessionOrgResolution
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class MaalingRoutesTest {

    private val maalingService = mockk<MaalingService>()
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
            configureSerialization()
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
                    maalingRoutes(maalingService, sessionOrgResolver)
                }
            }
        }
        client.block()
    }

    private fun maaling(patientId: PatientId, loincKode: String = "8310-5") =
        Maaling(
            id = MaalingId(Uuid.generateV4()),
            patientId = patientId,
            konsultasjonId = KonsultasjonId(Uuid.generateV4()),
            hpr = null,
            loincKode = loincKode,
            loincVisningsnavn = "Body temperature",
            verdi = BigDecimal("37.2"),
            enhetKode = "Cel",
            enhetVisningsnavn = "degree Celsius",
            effektivTidspunkt = LocalDateTime.of(2026, 9, 30, 9, 15),
            status = MaalingStatus.FINAL,
        )

    @Test
    fun `GET maalinger returns the persisted measurements for the patient`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        val maaling = maaling(patientId)
        coEvery { maalingService.getMaalingerForPatient(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(maaling)

        val response = get("/api/patient/${patientId.value}/maalinger")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(maaling.id.value.toString()))
        assertTrue(body.contains("8310-5"))
        assertTrue(body.contains("Body temperature"))
        assertTrue(body.contains("37.2"))
        assertTrue(body.contains("Cel"))
        assertTrue(body.contains("2026-09-30T09:15:00Z"))
        assertTrue(body.contains("FINAL"))
    }

    @Test
    fun `GET maalinger returns an empty array when the patient has none`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { maalingService.getMaalingerForPatient(patientId, DEFAULT_ORG_NUMBER) } returns
            emptyList()

        val response = get("/api/patient/${patientId.value}/maalinger")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[ ]", response.bodyAsText().trim())
    }

    @Test
    fun `GET maalinger only returns records for the requested patient`() = testApp {
        val patientA = PatientId(Uuid.generateV4())
        val patientB = PatientId(Uuid.generateV4())
        coEvery { maalingService.getMaalingerForPatient(patientA, DEFAULT_ORG_NUMBER) } returns
            listOf(maaling(patientA, "8310-5"))
        coEvery { maalingService.getMaalingerForPatient(patientB, DEFAULT_ORG_NUMBER) } returns
            listOf(maaling(patientB, "8867-4"))

        val body = get("/api/patient/${patientA.value}/maalinger").bodyAsText()

        assertTrue(body.contains(patientA.value.toString()))
        assertFalse(body.contains(patientB.value.toString()))
        assertFalse(body.contains("8867-4"))
        coVerify(exactly = 0) {
            maalingService.getMaalingerForPatient(patientB, DEFAULT_ORG_NUMBER)
        }
    }

    @Test
    fun `GET maalinger rejects a malformed patient id`() = testApp {
        val response = get("/api/patient/not-a-uuid/maalinger")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
