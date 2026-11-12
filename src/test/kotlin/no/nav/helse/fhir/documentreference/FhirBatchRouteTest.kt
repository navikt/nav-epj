@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.OperationOutcome
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.smart.security.Interaction
import org.junit.Test

class FhirBatchRouteTest : FhirWriteBackTestSupport() {
    @Test
    fun `a batch stores the PDF even when its QuestionnaireResponse is invalid`() {
        val (patientId, consultationId) = runBlockingFixture()
        val documentId = Uuid.generateV4()
        val questionnaireResponseId = Uuid.generateV4()
        testApp {
            val body =
                batch(
                    entry(
                        "PUT",
                        "DocumentReference/$documentId",
                        documentReference(
                            documentId.toString(),
                            patientId,
                            consultationId,
                            related = questionnaireResponseId,
                        ),
                    ),
                    entry(
                        "PUT",
                        "QuestionnaireResponse/$questionnaireResponseId",
                        questionnaireResponse(
                            questionnaireResponseId.toString(),
                            patientId,
                            ConsultationId(Uuid.generateV4()),
                        ),
                    ),
                )

            val response = postJson("/fhir", body)

            assertEquals(HttpStatusCode.OK, response.status)
            val bundle = decodeWire(response.bodyAsText()) as Bundle
            assertEquals(
                listOf("201 Created", "400 Bad Request"),
                bundle.entry.map { it.response?.status?.value },
            )
            assertEquals(
                "$FHIR_BASE/DocumentReference/$documentId",
                bundle.entry[0].response?.location?.value,
            )
            assertTrue(bundle.entry[1].response?.outcome is OperationOutcome)
            assertEquals(HttpStatusCode.OK, get("/fhir/DocumentReference/$documentId").status)
            assertEquals(
                HttpStatusCode.NotFound,
                get("/fhir/QuestionnaireResponse/$questionnaireResponseId").status,
            )
        }
    }

    @Test
    fun `a batch is accepted at the base URL with and without a trailing slash`() {
        val (patientId, _) = runBlockingFixture()
        testApp {
            val read = batch(entry("GET", "DocumentReference?patient=Patient/${patientId.value}"))
            val withoutSlash = postJson("/fhir", read)
            val withSlash = postJson("/fhir/", read)

            assertEquals(HttpStatusCode.OK, withoutSlash.status)
            assertEquals(HttpStatusCode.OK, withSlash.status)
            val bundle = decodeWire(withSlash.bodyAsText()) as Bundle
            assertEquals("200 OK", bundle.entry.single().response?.status?.value)
        }
    }

    @Test
    fun `a batch applies scope per entry with the outer principal`() {
        val (patientId, consultationId) = runBlockingFixture()
        val readable = Uuid.generateV4()
        val denied = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/DocumentReference/$readable",
                documentReference(readable.toString(), patientId, consultationId),
            )
        }
        testApp(setOf(scope("DocumentReference", Interaction.READ, Interaction.CREATE))) {
            val response =
                postJson(
                    "/fhir",
                    batch(
                        entry("GET", "DocumentReference/$readable"),
                        entry("DELETE", "DocumentReference/$readable"),
                        entry(
                            "PUT",
                            "DocumentReference/$denied",
                            documentReference(denied.toString(), patientId, consultationId),
                        ),
                        entry("GET", "QuestionnaireResponse/${Uuid.generateV4()}"),
                    ),
                )

            val bundle = decodeWire(response.bodyAsText()) as Bundle
            assertEquals(
                listOf("200 OK", "403 Forbidden", "201 Created", "403 Forbidden"),
                bundle.entry.map { it.response?.status?.value },
            )
            assertEquals(HttpStatusCode.OK, get("/fhir/DocumentReference/$readable").status)
        }
    }

    @Test
    fun `a batch rejects transactions, absolute URLs and too many entries`() {
        testApp {
            val transaction = postJson("/fhir", batch(type = "transaction"))
            val tooMany =
                postJson(
                    "/fhir",
                    batch(*Array(11) { entry("GET", "DocumentReference/${Uuid.generateV4()}") }),
                )
            val mixed =
                postJson(
                    "/fhir",
                    batch(
                        entry(
                            "GET",
                            "http://evil.test/fhir/DocumentReference/${Uuid.generateV4()}",
                        ),
                        entry("GET", "Patient/${Uuid.generateV4()}"),
                        entry("GET", "DocumentReference/${Uuid.generateV4()}"),
                    ),
                )

            assertEquals(HttpStatusCode.BadRequest, transaction.status)
            assertEquals(HttpStatusCode.BadRequest, tooMany.status)
            val bundle = decodeWire(mixed.bodyAsText()) as Bundle
            assertEquals(
                listOf("400 Bad Request", "400 Bad Request", "404 Not Found"),
                bundle.entry.map { it.response?.status?.value },
            )
        }
    }

    @Test
    fun `a batch body above the limit is rejected`() {
        testApp {
            val post =
                post("/fhir") {
                    contentType(FHIR_JSON)
                    setBody(ByteArray(3_500_001))
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, post.status)
        }
    }
}
