@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.ehr.consultation

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson3.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.core.db.ClinicalNoteAttachmentTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.ehr.clinic.Clinic
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.consultation.routes.clinicalNoteRoutes
import no.nav.helse.ehr.sessionorg.SessionOrgResolution
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseRepository
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseWrite
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.ensureOrg
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.Test

class ClinicalNoteRoutesTest : ConsultationRepositoryTestBase() {

    private val service = ConsultationService(consultationRepository, clinicalDocumentRepository)
    private val questionnaireResponseRepository = QuestionnaireResponseRepository()
    private val pdf = "%PDF-1.4 innhold".toByteArray()

    private fun testApp(
        orgNumber: String = DEFAULT_ORG_NUMBER,
        block: suspend HttpClient.() -> Unit,
    ) = testApplication {
        val resolver =
            mockk<SessionOrgResolver> {
                coEvery { resolve(any()) } returns
                    SessionOrgResolution.Resolved(
                        Clinic(ClinicId(Uuid.generateV4()), "Fjordvik", orgNumber, null),
                        derived = true,
                    )
            }
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
            routing { authenticate("wonderwall-helseid") { clinicalNoteRoutes(service, resolver) } }
        }
        client.block()
    }

    private fun fixture(
        title: String? = "sykmelding.pdf",
        expiredDays: Long? = null,
        relatedQuestionnaireResponse: Boolean = false,
    ): Triple<ClinicalNoteId, Uuid?, ConsultationId> = runBlocking {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val questionnaireResponseId =
            if (relatedQuestionnaireResponse) {
                Uuid.generateV4().also {
                    questionnaireResponseRepository.write(
                        it,
                        QuestionnaireResponseWrite(patientId, consultationId, emptyList(), "{}"),
                        DEFAULT_ORG_NUMBER,
                        allowCreate = true,
                        allowUpdate = true,
                    )
                }
            } else null
        val id =
            writeFhirNote(
                patientId,
                consultationId,
                "tekst",
                attachment = NewAttachment(title, "no-NO", pdf),
                relatedQuestionnaireResponseId = questionnaireResponseId,
            )
        if (expiredDays != null) {
            dbQueryBackdate(id, expiredDays)
        }
        Triple(id, questionnaireResponseId, consultationId)
    }

    private suspend fun dbQueryBackdate(id: ClinicalNoteId, days: Long) = dbQuery {
        ClinicalNoteAttachmentTable.update({
            ClinicalNoteAttachmentTable.clinicalNoteId eq id.value
        }) {
            it[createdAt] = Instant.now().minusSeconds(days * 24 * 3600)
        }
    }

    @Test
    fun `the attachment is served inline with a safe and UTF-8 filename`() {
        val (id, _, _) = fixture(title = "Sykmelding blå\".pdf")

        testApp {
            val response = get("/api/clinical-notes/${id.value}/attachment")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType()?.withoutParameters())
            assertEquals(
                "inline; filename=\"Sykmelding bl__.pdf\"; " +
                    "filename*=UTF-8''Sykmelding%20bl%C3%A5%22.pdf",
                response.headers[HttpHeaders.ContentDisposition],
            )
            assertContentEquals(pdf, response.bodyAsBytes())
        }
    }

    @Test
    fun `an expired attachment is not served`() {
        val (id, _, _) = fixture(expiredDays = 15)

        testApp {
            assertEquals(
                HttpStatusCode.NotFound,
                get("/api/clinical-notes/${id.value}/attachment").status,
            )
            assertEquals(HttpStatusCode.OK, get("/api/clinical-notes/${id.value}").status)
        }
    }

    @Test
    fun `an attachment of another org is not served`() {
        runBlocking { ensureOrg(OTHER_ORG_NUMBER) }
        val (id, _, _) = fixture()

        testApp(OTHER_ORG_NUMBER) {
            assertEquals(
                HttpStatusCode.NotFound,
                get("/api/clinical-notes/${id.value}/attachment").status,
            )
            assertEquals(HttpStatusCode.NotFound, delete("/api/clinical-notes/${id.value}").status)
        }
    }

    @Test
    fun `delete removes the FHIR note and its linked questionnaire response`() {
        val (id, questionnaireResponseId, _) = fixture(relatedQuestionnaireResponse = true)

        testApp {
            assertEquals(HttpStatusCode.NoContent, delete("/api/clinical-notes/${id.value}").status)
            assertEquals(HttpStatusCode.NotFound, get("/api/clinical-notes/${id.value}").status)
            assertEquals(HttpStatusCode.NotFound, delete("/api/clinical-notes/${id.value}").status)
        }
        runBlocking {
            assertNull(
                questionnaireResponseRepository.find(questionnaireResponseId!!, DEFAULT_ORG_NUMBER)
            )
        }
    }

    @Test
    fun `delete refuses a note written by the EHR`() {
        val id = ClinicalNoteId(Uuid.generateV4())
        runBlocking {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, consultationId, patientId, "egen tekst"),
                DEFAULT_ORG_NUMBER,
            )
        }

        testApp {
            assertEquals(HttpStatusCode.Conflict, delete("/api/clinical-notes/${id.value}").status)
        }
        runBlocking {
            assertNotNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
        }
    }
}
