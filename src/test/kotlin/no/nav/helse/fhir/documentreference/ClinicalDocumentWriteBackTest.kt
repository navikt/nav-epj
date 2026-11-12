@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
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
import java.time.Instant
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicalNoteAttachmentTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.ehr.consultation.ClinicalNoteId
import no.nav.helse.ehr.consultation.ClinicalNoteSource
import no.nav.helse.fhir.FHIR_BASE
import no.nav.helse.fhir.FHIR_JSON
import no.nav.helse.fhir.FhirWriteBackTestSupport
import no.nav.helse.fhir.HPR
import no.nav.helse.smart.security.Interaction
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.ensureOrg
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.Test

class ClinicalDocumentWriteBackTest : FhirWriteBackTestSupport() {
    @Test
    fun `PUT creates a DocumentReference with 201 and Location and updates it with 200`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val id = Uuid.generateV4()

            val created =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId),
                )
            val updated =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId, title = "ny.pdf"),
                )

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("$FHIR_BASE/DocumentReference/$id", created.headers[HttpHeaders.Location])
            assertEquals(HttpStatusCode.OK, updated.status)
            assertEquals("$FHIR_BASE/DocumentReference/$id", updated.headers[HttpHeaders.Location])
            val stored =
                decodeWire(get("/fhir/DocumentReference/$id").bodyAsText()) as DocumentReference
            val attachment = stored.content.single().attachment
            assertEquals("ny.pdf", attachment.title?.value)
            assertEquals("Sykmelding", stored.type?.text?.value)
            assertEquals("Practitioner/$HPR", stored.author.single().reference?.value)
            assertEquals("2025-01-10", stored.context?.period?.end?.value.toString())
            assertContentEquals(pdfBytes(), Base64.getDecoder().decode(attachment.data?.value))
        }
    }

    @Test
    fun `the stored language is returned as NO-nb on the wire`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val id = Uuid.generateV4()
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId),
            )

            val body = get("/fhir/DocumentReference/$id").bodyAsText()

            assertTrue(Regex("\"language\"\\s*:\\s*\"NO-nb\"").containsMatchIn(body), body)
        }
    }

    @Test
    fun `POST assigns the id on the server`() {
        testApp {
            val (patientId, consultationId) = consultationFor()

            val response =
                postJson(
                    "/fhir/DocumentReference",
                    documentReference("client-chosen", patientId, consultationId),
                )

            assertEquals(HttpStatusCode.Created, response.status)
            val location = response.headers[HttpHeaders.Location]!!
            assertTrue(location.startsWith("$FHIR_BASE/DocumentReference/"))
            assertTrue(!location.endsWith("client-chosen"))
        }
    }

    @Test
    fun `PUT requires a body id equal to the URL id`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val urlId = Uuid.generateV4()

            val mismatched =
                putJson(
                    "/fhir/DocumentReference/$urlId",
                    documentReference(Uuid.generateV4().toString(), patientId, consultationId),
                )
            val missing =
                putJson(
                    "/fhir/DocumentReference/$urlId",
                    documentReference(null, patientId, consultationId),
                )

            assertEquals(HttpStatusCode.BadRequest, mismatched.status)
            assertEquals(HttpStatusCode.BadRequest, missing.status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$urlId").status)
        }
    }

    @Test
    fun `PUT needs CREATE for a new document and UPDATE for an existing one`() {
        val createOnly = setOf(scope("DocumentReference", Interaction.CREATE))
        val updateOnly = setOf(scope("DocumentReference", Interaction.UPDATE))
        val (patientId, consultationId) = runBlockingFixture()
        val existing = Uuid.generateV4()
        val fresh = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/DocumentReference/$existing",
                documentReference(existing.toString(), patientId, consultationId),
            )
        }

        testApp(createOnly) {
            val update =
                putJson(
                    "/fhir/DocumentReference/$existing",
                    documentReference(
                        existing.toString(),
                        patientId,
                        consultationId,
                        title = "x.pdf",
                    ),
                )
            val create =
                putJson(
                    "/fhir/DocumentReference/$fresh",
                    documentReference(fresh.toString(), patientId, consultationId),
                )

            assertEquals(HttpStatusCode.Forbidden, update.status)
            assertEquals(HttpStatusCode.Created, create.status)
        }
        val fresh2 = Uuid.generateV4()
        testApp(updateOnly) {
            val create =
                putJson(
                    "/fhir/DocumentReference/$fresh2",
                    documentReference(fresh2.toString(), patientId, consultationId),
                )
            val update =
                putJson(
                    "/fhir/DocumentReference/$existing",
                    documentReference(
                        existing.toString(),
                        patientId,
                        consultationId,
                        title = "u.pdf",
                    ),
                )

            assertEquals(HttpStatusCode.Forbidden, create.status)
            assertEquals(HttpStatusCode.OK, update.status)
        }
        testApp {
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$fresh2").status)
        }
    }

    @Test
    fun `a patient bound token cannot write for another patient`() {
        val (patientId, consultationId) = runBlockingFixture()
        val id = Uuid.generateV4()
        testApp(allWritesForPatient, boundPatient = Uuid.generateV4().toString()) {
            val response =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId),
                )

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
        testApp { assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$id").status) }
    }

    @Test
    fun `an existing document cannot be overwritten from another org or moved to another encounter`() {
        val (patientId, consultationId) = runBlockingFixture()
        val otherConsultationId = kotlinx.coroutines.runBlocking { createConsultation(patientId) }
        val id = Uuid.generateV4()
        kotlinx.coroutines.runBlocking { ensureOrg(OTHER_ORG_NUMBER) }
        testApp {
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId),
            )

            val moved =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, otherConsultationId),
                )

            assertEquals(HttpStatusCode.Conflict, moved.status)
        }
        testApp(orgNumber = OTHER_ORG_NUMBER) {
            val response =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId, title = "x.pdf"),
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$id").status)
        }
        testApp {
            val stored =
                decodeWire(get("/fhir/DocumentReference/$id").bodyAsText()) as DocumentReference
            assertEquals("Sykmelding æøå.pdf", stored.content.single().attachment.title?.value)
        }
    }

    @Test
    fun `an EHR note cannot be overwritten or deleted through FHIR`() {
        val (patientId, consultationId) = runBlockingFixture()
        val id = ClinicalNoteId(Uuid.generateV4())
        kotlinx.coroutines.runBlocking {
            consultationRepository.insertClinicalNote(
                no.nav.helse.ehr.consultation.ClinicalNote(
                    id,
                    consultationId,
                    patientId,
                    "journal",
                ),
                DEFAULT_ORG_NUMBER,
            )
        }
        testApp {
            val put =
                putJson(
                    "/fhir/DocumentReference/${id.value}",
                    documentReference(id.value.toString(), patientId, consultationId),
                )
            val delete = delete("/fhir/DocumentReference/${id.value}")

            assertEquals(HttpStatusCode.Conflict, put.status)
            assertEquals(HttpStatusCode.Conflict, delete.status)
            assertEquals(
                "journal",
                kotlinx.coroutines.runBlocking {
                    consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)?.clinicalNote
                },
            )
        }
    }

    @Test
    fun `an author that is not on the consultation is rejected`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val id = Uuid.generateV4()

            val response =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId, author = "999"),
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$id").status)
        }
    }

    @Test
    fun `DELETE needs the DELETE scope and leaves a related QuestionnaireResponse`() {
        val (patientId, consultationId) = runBlockingFixture()
        val questionnaireResponseId = Uuid.generateV4()
        val id = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/QuestionnaireResponse/$questionnaireResponseId",
                questionnaireResponse(questionnaireResponseId.toString(), patientId, consultationId),
            )
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(
                    id.toString(),
                    patientId,
                    consultationId,
                    related = questionnaireResponseId,
                ),
            )
        }
        testApp(setOf(scope("DocumentReference", Interaction.READ, Interaction.UPDATE))) {
            assertEquals(HttpStatusCode.Forbidden, delete("/fhir/DocumentReference/$id").status)
        }
        testApp {
            assertEquals(HttpStatusCode.NoContent, delete("/fhir/DocumentReference/$id").status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$id").status)
            assertEquals(
                HttpStatusCode.OK,
                get("/fhir/QuestionnaireResponse/$questionnaireResponseId").status,
            )
        }
    }

    @Test
    fun `a PDF of exactly 2000000 bytes is accepted and one byte more is rejected`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val ok = Uuid.generateV4()
            val tooBig = Uuid.generateV4()

            val accepted =
                putJson(
                    "/fhir/DocumentReference/$ok",
                    documentReference(
                        ok.toString(),
                        patientId,
                        consultationId,
                        base64(pdfBytes(2_000_000)),
                    ),
                )
            val rejected =
                putJson(
                    "/fhir/DocumentReference/$tooBig",
                    documentReference(
                        tooBig.toString(),
                        patientId,
                        consultationId,
                        base64(pdfBytes(2_000_001)),
                    ),
                )

            assertEquals(HttpStatusCode.Created, accepted.status)
            assertEquals(HttpStatusCode.PayloadTooLarge, rejected.status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$tooBig").status)
        }
    }

    @Test
    fun `an over-long base64 string is rejected before it is decoded`() {
        testApp {
            val (patientId, consultationId) = consultationFor()
            val id = Uuid.generateV4()

            val response =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(
                        id.toString(),
                        patientId,
                        consultationId,
                        "!".repeat(MAX_ATTACHMENT_BASE64_CHARS + 4),
                    ),
                )

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
    }

    @Test
    fun `an oversize body is rejected by Content-Length and when streamed without a length`() {
        testApp {
            val id = Uuid.generateV4()
            val oversize = 3_500_001

            val declared =
                put("/fhir/DocumentReference/$id") {
                    contentType(FHIR_JSON)
                    setBody(ByteArray(oversize))
                }
            val chunked =
                put("/fhir/DocumentReference/$id") {
                    contentType(FHIR_JSON)
                    setBody(
                        object : OutgoingContent.WriteChannelContent() {
                            override val contentLength: Long? = null

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                repeat(oversize / 65_536 + 1) {
                                    channel.writeFully(ByteArray(65_536))
                                }
                            }
                        }
                    )
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, declared.status)
            assertEquals(HttpStatusCode.PayloadTooLarge, chunked.status)
            val outcome = decodeWire(chunked.bodyAsText()) as OperationOutcome
            assertEquals(1, outcome.issue.size)
        }
    }

    @Test
    fun `a text-only FHIR note is stored with source FHIR and without attachment`() {
        val (patientId, consultationId) = runBlockingFixture()
        val id = Uuid.generateV4()
        testApp {
            val response =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId, data = null),
                )

            assertEquals(HttpStatusCode.Created, response.status)
            val note =
                kotlinx.coroutines.runBlocking {
                    consultationRepository.findClinicalNote(ClinicalNoteId(id), DEFAULT_ORG_NUMBER)
                }
            assertEquals(ClinicalNoteSource.FHIR, note?.source)
            assertEquals(false, note?.hasAttachment)
            assertNull(note?.attachmentTitle)
        }
    }

    @Test
    fun `an expired attachment round-trips as metadata only without renewing or clearing it`() {
        val (patientId, consultationId) = runBlockingFixture()
        val id = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId),
            )
            val live = get("/fhir/DocumentReference/$id").bodyAsText()

            val liveMetadataOnly = putJson("/fhir/DocumentReference/$id", withoutData(live))
            assertEquals(HttpStatusCode.BadRequest, liveMetadataOnly.status)
            assertTrue(storedAttachment(id).second != null)

            dbQuery {
                ClinicalNoteAttachmentTable.update({
                    ClinicalNoteAttachmentTable.clinicalNoteId eq id
                }) {
                    it[createdAt] = Instant.now().minusSeconds(15L * 24 * 3600)
                }
            }
            val expired = get("/fhir/DocumentReference/$id").bodyAsText()
            assertNull((decodeWire(expired) as DocumentReference).content.single().attachment.data)
            val before = storedAttachment(id)

            val updated =
                putJson(
                    "/fhir/DocumentReference/$id",
                    withoutData(expired).replace("Sykmelding sendt til Nav", "Endret"),
                )
            val mismatching =
                putJson(
                    "/fhir/DocumentReference/$id",
                    withoutData(expired).replace("Sykmelding æøå.pdf", "annen.pdf"),
                )

            assertEquals(HttpStatusCode.OK, updated.status)
            assertEquals(HttpStatusCode.BadRequest, mismatching.status)
            val after = storedAttachment(id)
            assertEquals(before.first, after.first)
            assertContentEquals(before.second, after.second)
            val note =
                consultationRepository.findClinicalNote(ClinicalNoteId(id), DEFAULT_ORG_NUMBER)
            assertEquals("Endret", note?.clinicalNote)
            assertEquals(true, note?.attachmentExpired)
            assertEquals("Sykmelding æøå.pdf", note?.attachmentTitle)

            val cleared =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId, data = null),
                )
            assertEquals(HttpStatusCode.OK, cleared.status)
            val afterClear =
                consultationRepository.findClinicalNote(ClinicalNoteId(id), DEFAULT_ORG_NUMBER)
            assertEquals(false, afterClear?.hasAttachment)
        }
    }

    @Test
    fun `a metadata only attachment cannot create a document`() {
        val (patientId, consultationId) = runBlockingFixture()
        val id = Uuid.generateV4()
        testApp {
            val body =
                documentReference(id.toString(), patientId, consultationId)
                    .replace(Regex(",\"data\":\"[^\"]*\""), "")

            assertEquals(
                HttpStatusCode.BadRequest,
                putJson("/fhir/DocumentReference/$id", body).status,
            )
        }
    }

    private fun withoutData(body: String) =
        body.replace(Regex("\"data\"\\s*:\\s*\"[^\"]*\"\\s*,"), "")

    private suspend fun storedAttachment(id: Uuid): Pair<Instant, ByteArray?> = dbQuery {
        ClinicalNoteAttachmentTable.selectAll()
            .where { ClinicalNoteAttachmentTable.clinicalNoteId eq id }
            .single()
            .let {
                it[ClinicalNoteAttachmentTable.createdAt] to it[ClinicalNoteAttachmentTable.data]
            }
    }
}
