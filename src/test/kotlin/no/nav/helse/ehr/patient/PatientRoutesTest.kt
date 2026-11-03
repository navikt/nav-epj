package no.nav.helse.ehr.patient

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.DuplicatePatientException
import no.nav.helse.core.utils.InvalidPersonidentException
import no.nav.helse.core.utils.NoFastlegelisteException
import no.nav.helse.ehr.clinic.Clinic
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.ehr.sessionorg.SessionOrgResolution
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureStatusPages
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class PatientRoutesTest {

    private val patientService = mockk<PatientService>()
    private val sessionOrgResolver =
        mockk<SessionOrgResolver> {
            coEvery { resolve(any()) } returns
                SessionOrgResolution.Resolved(
                    Clinic(ClinicId(Uuid.generateV4()), "Fjordvik", "311111116", null),
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
                    patientRoutes(patientService, sessionOrgResolver)
                }
            }
        }
        client.block()
    }

    private fun createPatientJson(personident: String, birthDate: String) =
        """
        {
          "givenName": "Kari",
          "familyName": "Nordmann",
          "personident": "$personident",
          "personidentType": "FNR",
          "birthDate": "$birthDate",
          "gender": "FEMALE"
        }
        """
            .trimIndent()

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST patient accepts ISO birthDate and serializes it back as the same ISO string`() =
        testApp {
            val birthDate = LocalDate.of(1985, 6, 15)
            val personident = "15068500017"
            val createdPatient =
                Patient(
                    id = PatientId(Uuid.generateV4()),
                    givenName = "Kari",
                    familyName = "Nordmann",
                    personident = personident,
                    personidentType = PersonidentType.FNR,
                    birthDate = birthDate,
                    gender = AdministrativeGender.FEMALE,
                )
            coEvery { patientService.createPatient(any(), "111", "311111116") } returns
                createdPatient

            val response =
                post("/api/patient") {
                    contentType(ContentType.Application.Json)
                    setBody(createPatientJson(personident, "1985-06-15"))
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = response.bodyAsText()
            assertTrue(Regex("\"birthDate\"\\s*:\\s*\"1985-06-15\"").containsMatchIn(body))
        }

    @Test
    fun `POST patient with invalid personident returns 400`() = testApp {
        val personident = "00000000000"
        coEvery { patientService.createPatient(any(), any(), any()) } throws
            InvalidPersonidentException("Personident $personident har ugyldig kontrollsiffer")

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(createPatientJson(personident, "1985-06-15"))
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertEquals("Ugyldig personident", body)
        assertFalse(body.contains(personident))
        assertFalse(body.contains("kontrollsiffer"))
    }

    @Test
    fun `POST patient with a duplicate personident returns 409`() = testApp {
        val personident = "15068500017"
        coEvery { patientService.createPatient(any(), any(), any()) } throws
            DuplicatePatientException()

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(createPatientJson(personident, "1985-06-15"))
            }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.bodyAsText()
        assertEquals("Pasienten finnes allerede", body)
        assertFalse(body.contains(personident))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET patient lists the patients at the session org`() = testApp {
        val patient =
            Patient(
                id = PatientId(Uuid.generateV4()),
                givenName = "Kari",
                familyName = "Nordmann",
                personident = "15068500017",
            )
        coEvery { patientService.listAtClinic("311111116", PractitionerHpr("111")) } returns
            listOf(
                PatientListItem(
                    patient,
                    selectable = false,
                    latestConsultation =
                        LatestConsultation(
                            ConsultationStatus.IN_PROGRESS,
                            LocalDateTime.parse("2026-01-02T03:04:05"),
                        ),
                )
            )

        val response = get("/api/patient")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Nordmann"))
        assertTrue(response.bodyAsText().contains("\"selectable\" : false"))
        assertTrue(response.bodyAsText().contains("\"status\" : \"IN_PROGRESS\""))
        assertTrue(response.bodyAsText().contains("\"timestamp\" : \"2026-01-02T03:04:05\""))
    }

    @Test
    fun `GET patient without a session org returns 409`() = testApp {
        coEvery { sessionOrgResolver.resolve("111") } returns SessionOrgResolution.NeedsChoice

        assertEquals(HttpStatusCode.Conflict, get("/api/patient").status)
    }

    @Test
    fun `POST patient without an own list at the org returns 403`() = testApp {
        coEvery { patientService.createPatient(any(), any(), any()) } throws
            NoFastlegelisteException()

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(createPatientJson("15068500017", "1985-06-15"))
            }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `GET patient by id returns a patient listed at the session org`() = testApp {
        val id = PatientId(Uuid.generateV4())
        coEvery { patientService.getPatientById(id) } returns
            Patient(id, "Kari", "Nordmann", "15068500017")
        coEvery { patientService.isListedAt(id, "311111116") } returns true

        assertEquals(HttpStatusCode.OK, get("/api/patient/${id.value}").status)
    }

    @Test
    fun `GET patient by id returns a patient with consultations at the session org that is no longer listed`() =
        testApp {
            val id = PatientId(Uuid.generateV4())
            coEvery { patientService.getPatientById(id) } returns
                Patient(id, "Kari", "Nordmann", "15068500017")
            coEvery { patientService.isListedAt(id, "311111116") } returns false
            coEvery { patientService.hasConsultationAt(id, "311111116") } returns true

            assertEquals(HttpStatusCode.OK, get("/api/patient/${id.value}").status)
        }

    @Test
    fun `GET patient by id returns 404 for a patient not listed at the session org`() = testApp {
        val id = PatientId(Uuid.generateV4())
        coEvery { patientService.getPatientById(id) } returns
            Patient(id, "Kari", "Nordmann", "15068500017")
        coEvery { patientService.isListedAt(id, "311111116") } returns false
        coEvery { patientService.hasConsultationAt(id, "311111116") } returns false

        assertEquals(HttpStatusCode.NotFound, get("/api/patient/${id.value}").status)
    }
}
