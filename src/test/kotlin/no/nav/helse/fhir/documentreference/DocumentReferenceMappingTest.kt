@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.fhir.FhirRequestException
import org.junit.Test

class DocumentReferenceMappingTest {
    private val patient = Uuid.generateV4()
    private val encounter = Uuid.generateV4()
    private val pdf = "%PDF-1.4".toByteArray()

    private fun documentReference(
        attachment: String =
            "\"contentType\":\"application/pdf\",\"language\":\"no-NO\",\"title\":\"a.pdf\"," +
                "\"data\":\"${Base64.getEncoder().encodeToString(pdf)}\"",
        context: String =
            "\"encounter\":[{\"reference\":\"Encounter/$encounter\"}]," +
                "\"period\":{\"start\":\"2025-01-01\",\"end\":\"2025-01-10\"}",
        extra: String = "",
    ) =
        FhirR4Json()
            .decodeFromString(
                "{\"resourceType\":\"DocumentReference\",\"status\":\"current\"," +
                    "\"type\":{\"coding\":[{\"system\":\"urn:oid:2.16.578.1.12.4.1.1.9602\"," +
                    "\"code\":\"J01-2\"}],\"text\":\"Sykmelding\"}," +
                    "\"subject\":{\"reference\":\"Patient/$patient\"}," +
                    "\"author\":[{\"reference\":\"Practitioner/123\"}]$extra," +
                    "\"content\":[{\"attachment\":{$attachment}}]," +
                    "\"context\":{$context}}"
            ) as DocumentReference

    private fun assertRejected(status: HttpStatusCode, resource: DocumentReference) {
        assertEquals(
            status,
            assertFailsWith<FhirRequestException> { resource.toClinicalDocumentWrite() }.status,
        )
    }

    @Test
    fun `the ADR fields are mapped`() {
        val related = Uuid.generateV4()

        val write =
            documentReference(
                    context =
                        "\"encounter\":[{\"reference\":\"Encounter/$encounter\"}]," +
                            "\"period\":{\"start\":\"2025-01-01\",\"end\":\"2025-01-10\"}," +
                            "\"related\":[{\"reference\":\"QuestionnaireResponse/$related\"}]"
                )
                .toClinicalDocumentWrite()

        assertEquals(patient, write.patientId.value)
        assertEquals(encounter, write.consultationId.value)
        assertEquals("Sykmelding", write.typeText)
        assertEquals(LocalDate.of(2025, 1, 1), write.periodStart)
        assertEquals(LocalDate.of(2025, 1, 10), write.periodEnd)
        assertEquals(related, write.relatedQuestionnaireResponseId)
        assertEquals(listOf("123"), write.authorHprs)
        assertEquals("a.pdf", write.attachment?.title)
        assertEquals("no-NO", write.attachment?.language)
        assertContentEquals(pdf, write.attachment?.data)
    }

    @Test
    fun `the related questionnaire response is optional and not required to exist`() {
        assertNull(documentReference().toClinicalDocumentWrite().relatedQuestionnaireResponseId)
    }

    @Test
    fun `a malformed or foreign related reference is rejected`() {
        val context = { related: String ->
            "\"encounter\":[{\"reference\":\"Encounter/$encounter\"}]," +
                "\"related\":[{\"reference\":\"$related\"}]"
        }

        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(context = context("QuestionnaireResponse/not-a-uuid")),
        )
        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(context = context("Patient/${Uuid.generateV4()}")),
        )
    }

    @Test
    fun `a period that ends before it starts is rejected`() {
        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(
                context =
                    "\"encounter\":[{\"reference\":\"Encounter/$encounter\"}]," +
                        "\"period\":{\"start\":\"2025-02-01\",\"end\":\"2025-01-01\"}"
            ),
        )
    }

    @Test
    fun `title and language without data are a retained attachment, not a new one`() {
        val write =
            documentReference(attachment = "\"contentType\":\"application/pdf\",\"title\":\"a\"")
                .toClinicalDocumentWrite()

        assertNull(write.attachment)
        assertEquals("a", write.retainedAttachment?.title)
    }

    @Test
    fun `an attachment without data is allowed`() {
        assertNull(
            documentReference(attachment = "\"contentType\":\"application/pdf\"")
                .toClinicalDocumentWrite()
                .let { it.attachment ?: it.retainedAttachment }
        )
    }

    @Test
    fun `data that is not a PDF or not base64 is rejected`() {
        val notPdf = Base64.getEncoder().encodeToString("hello world".toByteArray())

        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(
                attachment = "\"contentType\":\"application/pdf\",\"data\":\"$notPdf\""
            ),
        )
        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(attachment = "\"contentType\":\"application/pdf\",\"data\":\"***\""),
        )
    }

    @Test
    fun `other content types and unsupported attachment fields are rejected`() {
        val data = Base64.getEncoder().encodeToString(pdf)

        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(attachment = "\"contentType\":\"text/plain\",\"data\":\"$data\""),
        )
        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(
                attachment = "\"contentType\":\"application/pdf\",\"size\":8,\"data\":\"$data\""
            ),
        )
        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(
                attachment =
                    "\"contentType\":\"application/pdf\",\"url\":\"http://x\",\"data\":\"$data\""
            ),
        )
    }

    @Test
    fun `unsupported top level fields are rejected`() {
        assertRejected(
            HttpStatusCode.BadRequest,
            documentReference(extra = ",\"date\":\"2025-01-01T10:00:00Z\""),
        )
    }

    @Test
    fun `an attachment above the decoded limit is rejected as too large`() {
        val tooLarge =
            Base64.getEncoder().encodeToString(pdf + ByteArray(MAX_ATTACHMENT_BYTES - pdf.size + 1))

        assertRejected(
            HttpStatusCode.PayloadTooLarge,
            documentReference(
                attachment = "\"contentType\":\"application/pdf\",\"data\":\"$tooLarge\""
            ),
        )
    }
}
