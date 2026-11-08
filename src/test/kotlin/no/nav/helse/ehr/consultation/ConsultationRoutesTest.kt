package no.nav.helse.ehr.consultation

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
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationNotFoundForPatientException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.DuplicateDiagnosisException
import no.nav.helse.ehr.clinic.Clinic
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.consultation.routes.consultationRoutes
import no.nav.helse.ehr.patient.ActivePatientService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.patient.PatientService
import no.nav.helse.ehr.sessionorg.SessionOrgResolution
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class ConsultationRoutesTest {

    private val consultationService = mockk<ConsultationService>()
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
                    consultationRoutes(
                        consultationService,
                        ActivePatientService(patientService, valkeyService, sessionOrgResolver),
                        sessionOrgResolver,
                    )
                }
            }
        }
        client.block()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun consultation(
        id: ConsultationId = ConsultationId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
    ) =
        Consultation(
            id = id,
            patientId = patientId,
            hpr = emptyList(),
            clinicId = ClinicId(Uuid.generateV4()),
            clinicalNotes = emptyList(),
            diagnoses = emptyList(),
            startedAt = LocalDateTime.now().minusDays(1),
            endedAt = null,
            status = ConsultationStatus.IN_PROGRESS,
            reasonForVisit = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET consultation with known id returns 200`() = testApp {
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(id = consultationId)

        val response = get("/api/consultations/${consultationId.value}")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET consultation with unknown id returns 404`() = testApp {
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } throws
            ConsultationNotFoundException(consultationId)

        val response = get("/api/consultations/${consultationId.value}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET consultations for unknown patient returns 404`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } throws
            ConsultationNotFoundForPatientException(patientId)

        val response = get("/api/patients/${patientId.value}/consultations")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET consultations does not change the active patient`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(consultation(patientId = patientId))

        val response = get("/api/patients/${patientId.value}/consultations")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST consultations for a listed patient starts it and makes it active`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { patientService.isListedAt(patientId, DEFAULT_ORG_NUMBER) } returns true
        coEvery { valkeyService.getActivePatientWithExpiry("111", DEFAULT_ORG_NUMBER) } returns
            ActivePatient(patientId.value.toString(), Instant.now().plusSeconds(60))
        coEvery {
            consultationService.getOrCreateConsultation(patientId, any(), DEFAULT_ORG_NUMBER)
        } returns consultation(patientId = patientId)

        val response = post("/api/patients/${patientId.value}/consultations")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) {
            valkeyService.setActivePatient("111", DEFAULT_ORG_NUMBER, patientId.value.toString())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST consultations for a patient not on a list at the session org returns 404 and changes nothing`() =
        testApp {
            val patientId = PatientId(Uuid.generateV4())
            coEvery { patientService.isListedAt(patientId, DEFAULT_ORG_NUMBER) } returns false

            val response = post("/api/patients/${patientId.value}/consultations")

            assertEquals(HttpStatusCode.NotFound, response.status)
            coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
            coVerify(exactly = 0) {
                consultationService.getOrCreateConsultation(any(), any(), any())
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST consultations without a session org returns 409 and changes nothing`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { sessionOrgResolver.resolve("111") } returns SessionOrgResolution.NeedsChoice

        val response = post("/api/patients/${patientId.value}/consultations")

        assertEquals(HttpStatusCode.Conflict, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
        coVerify(exactly = 0) { consultationService.getOrCreateConsultation(any(), any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST consultations for an unknown patient returns 404 and changes nothing`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        coEvery { patientService.isListedAt(patientId, DEFAULT_ORG_NUMBER) } returns false

        val response = post("/api/patients/${patientId.value}/consultations")

        assertEquals(HttpStatusCode.NotFound, response.status)
        coVerify(exactly = 0) { valkeyService.setActivePatient(any(), any(), any()) }
        coVerify(exactly = 0) { consultationService.getOrCreateConsultation(any(), any(), any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST cancel cancels the consultation and returns 200`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery {
            consultationService.cancelConsultation(consultationId, patientId, DEFAULT_ORG_NUMBER)
        } returns Unit

        val response =
            post("/api/patients/${patientId.value}/consultations/${consultationId.value}/cancel")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) {
            consultationService.cancelConsultation(consultationId, patientId, DEFAULT_ORG_NUMBER)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST cancel for a consultation not owned by the patient returns 404`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery {
            consultationService.cancelConsultation(consultationId, patientId, DEFAULT_ORG_NUMBER)
        } throws ConsultationNotFoundForPatientException(patientId)

        val response =
            post("/api/patients/${patientId.value}/consultations/${consultationId.value}/cancel")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `PATCH consultation with a repeated diagnosis returns 400`() = testApp {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery {
            consultationService.updateConsultation(any(), patientId, DEFAULT_ORG_NUMBER)
        } throws DuplicateDiagnosisException("A01", "ICPC2")

        val response =
            patch("/api/patients/${patientId.value}/consultations") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"consultationId":"${consultationId.value}","diagnoses":[{"code":"A01","system":"ICPC2"},{"code":"A01","system":"ICPC2"}],"clinicalNote":null,"finalize":false}"""
                )
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
