@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.QuestionnaireResponse
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
import no.nav.helse.fhir.FHIR_BASE
import no.nav.helse.fhir.FhirWriteBackTestSupport
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.ensureOrg
import org.junit.Test

class QuestionnaireResponseTest : FhirWriteBackTestSupport() {
    @Test
    fun `a QuestionnaireResponse is stored standalone with nested items and searched by patient`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val id = Uuid.generateV4()

            val created =
                putJson(
                    "/fhir/QuestionnaireResponse/$id",
                    questionnaireResponse(id.toString(), patientId, consultationId),
                )
            val read = get("/fhir/QuestionnaireResponse/$id")
            val search = get("/fhir/QuestionnaireResponse?patient=Patient/${patientId.value}")

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(
                "$FHIR_BASE/QuestionnaireResponse/$id",
                created.headers[HttpHeaders.Location],
            )
            val stored = decodeWire(read.bodyAsText()) as QuestionnaireResponse
            assertEquals("1.1", stored.item.single().item.single().linkId.value)
            assertTrue("nestet" in read.bodyAsText())
            val bundle = decodeWire(search.bodyAsText()) as Bundle
            assertEquals(listOf(id.toString()), bundle.entry.map { it.resource?.id })
            assertEquals(
                HttpStatusCode.OK,
                putJson(
                        "/fhir/QuestionnaireResponse/$id",
                        questionnaireResponse(id.toString(), patientId, consultationId),
                    )
                    .status,
            )
            assertEquals(HttpStatusCode.NoContent, delete("/fhir/QuestionnaireResponse/$id").status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/QuestionnaireResponse/$id").status)
        }
    }

    @Test
    fun `a QuestionnaireResponse of any questionnaire and status is stored and read back unchanged`() {
        val canonical = "https://example.test/Questionnaire/other|2.0"
        testApp {
            val (patientId, consultationId) = consultationFor()
            val id = Uuid.generateV4()
            val body =
                questionnaireResponse(id.toString(), patientId, consultationId, canonical)
                    .replace("\"status\":\"completed\"", "\"status\":\"in-progress\"")

            val created = putJson("/fhir/QuestionnaireResponse/$id", body)
            val stored =
                decodeWire(get("/fhir/QuestionnaireResponse/$id").bodyAsText())
                    as QuestionnaireResponse

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(canonical, stored.questionnaire?.value)
            assertEquals(
                QuestionnaireResponse.QuestionnaireResponseStatus.In_Progress,
                stored.status.value,
            )
        }
    }

    @Test
    fun `QuestionnaireResponse reads are scoped to the patient and org`() {
        val (patientId, consultationId) = runBlockingFixture()
        val id = Uuid.generateV4()
        kotlinx.coroutines.runBlocking { ensureOrg(OTHER_ORG_NUMBER) }
        testApp {
            putJson(
                "/fhir/QuestionnaireResponse/$id",
                questionnaireResponse(id.toString(), patientId, consultationId),
            )
        }
        testApp(allWritesForPatient, boundPatient = Uuid.generateV4().toString()) {
            assertEquals(HttpStatusCode.NotFound, get("/fhir/QuestionnaireResponse/$id").status)
            assertEquals(HttpStatusCode.NotFound, delete("/fhir/QuestionnaireResponse/$id").status)
        }
        testApp(orgNumber = OTHER_ORG_NUMBER) {
            assertEquals(HttpStatusCode.NotFound, get("/fhir/QuestionnaireResponse/$id").status)
            assertEquals(HttpStatusCode.NotFound, delete("/fhir/QuestionnaireResponse/$id").status)
        }
        testApp { assertEquals(HttpStatusCode.OK, get("/fhir/QuestionnaireResponse/$id").status) }
    }
}
