@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Attachment
import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.DuplikatJournalnotatException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.OpprettJournalnotatRequest
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import org.junit.Test

private const val TYPE_SYSTEM = "urn:oid:2.16.578.1.12.4.1.1.9602"
private const val TYPE_CODE = "J01-2"

class DocumentReferenceRoutesTest {

    private val documentReferenceService = mockk<DocumentReferenceService>()
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")
    private val fhirServerUrl = "https://fhir.example.test/fhir"

    private fun documentReferenceScope(interactions: Set<Interaction>) =
        SmartScope.Fhir(
            context = ScopeContext.PATIENT,
            resourceType = "DocumentReference",
            interactions = interactions,
        )

    private fun sampleDocumentReference(
        id: String,
        patientId: String,
        encounterId: String = Uuid.generateV4().toString(),
    ): DocumentReference =
        DocumentReference(
            id = id,
            status = Enumeration(value = DocumentReferenceStatus.Current),
            type =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = TYPE_SYSTEM),
                                code = Code(value = TYPE_CODE),
                            )
                        )
                ),
            subject = Reference(reference = FhirString(value = "Patient/$patientId")),
            context =
                DocumentReference.Context(
                    encounter =
                        listOf(Reference(reference = FhirString(value = "Encounter/$encounterId")))
                ),
            content =
                listOf(
                    DocumentReference.Content(
                        attachment = Attachment(contentType = Code(value = "application/pdf"))
                    )
                ),
            description = FhirString(value = "notat"),
        )

    private fun testApp(
        scopes: Set<SmartScope> =
            setOf(documentReferenceScope(setOf(Interaction.READ, Interaction.SEARCH))),
        boundPatient: String? = null,
        block: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        application {
            configureStatusPages()
            authentication {
                provider("smart-access-token") {
                    authenticate { ctx ->
                        ctx.principal(
                            SmartPrincipal(
                                subject = "test-client",
                                scopes = scopes,
                                patient = boundPatient,
                                encounter = null,
                            )
                        )
                    }
                }
            }
            routing {
                authenticate("smart-access-token") {
                    documentReferenceRoutes(
                        documentReferenceService,
                        fhirJson,
                        fhirContentType,
                        fhirServerUrl,
                    )
                }
            }
        }
        client.block()
    }

    @Test
    fun `GET DocumentReference by id returns the mapped resource for the launched patient`() {
        val patientId = Uuid.generateV4().toString()
        val documentReferenceId = DocumentReferenceId(Uuid.generateV4())
        coEvery { documentReferenceService.getDocumentReferences(documentReferenceId) } returns
            sampleDocumentReference(documentReferenceId.value.toString(), patientId)

        testApp(boundPatient = patientId) {
            val response = get("/fhir/DocumentReference/${documentReferenceId.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "application/fhir+json",
                response.contentType()?.withoutParameters().toString(),
            )
            assertEquals(true, response.bodyAsText().contains("\"$TYPE_CODE\""))
        }
    }

    @Test
    fun `GET DocumentReference by id returns 404 for an unknown documentreference`() {
        val documentReferenceId = DocumentReferenceId(Uuid.generateV4())
        coEvery { documentReferenceService.getDocumentReferences(documentReferenceId) } returns null

        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response = get("/fhir/DocumentReference/${documentReferenceId.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `GET DocumentReference by id rejects a token missing the DocumentReference scope`() {
        val patientId = Uuid.generateV4().toString()
        val documentReferenceId = DocumentReferenceId(Uuid.generateV4())
        coEvery { documentReferenceService.getDocumentReferences(documentReferenceId) } returns
            sampleDocumentReference(documentReferenceId.value.toString(), patientId)

        testApp(scopes = emptySet(), boundPatient = patientId) {
            val response = get("/fhir/DocumentReference/${documentReferenceId.value}")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `GET DocumentReference by id returns 404 for a token bound to a different patient`() {
        val documentReferenceId = DocumentReferenceId(Uuid.generateV4())
        coEvery { documentReferenceService.getDocumentReferences(documentReferenceId) } returns
            sampleDocumentReference(
                documentReferenceId.value.toString(),
                Uuid.generateV4().toString(),
            )

        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response = get("/fhir/DocumentReference/${documentReferenceId.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `GET DocumentReference search requires a subject or patient parameter`() {
        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response = get("/fhir/DocumentReference")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET DocumentReference search rejects conflicting subject and patient parameters`() {
        val patientId = Uuid.generateV4().toString()
        val otherPatientId = Uuid.generateV4().toString()

        testApp(boundPatient = patientId) {
            val response =
                get(
                    "/fhir/DocumentReference?subject=Patient/$patientId&patient=Patient/$otherPatientId"
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET DocumentReference search accepts the patient alias parameter`() {
        val patientId = Uuid.generateV4()
        coEvery {
            documentReferenceService.searchDocumentReferences(PatientInputId(patientId), null)
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response = get("/fhir/DocumentReference?patient=Patient/$patientId")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET DocumentReference search passes the encounter filter through`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        coEvery {
            documentReferenceService.searchDocumentReferences(
                PatientInputId(patientId),
                EncounterId(encounterId),
            )
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response =
                get(
                    "/fhir/DocumentReference?subject=Patient/$patientId&encounter=Encounter/$encounterId"
                )

            assertEquals(HttpStatusCode.OK, response.status)
        }
        coVerify(exactly = 1) {
            documentReferenceService.searchDocumentReferences(
                PatientInputId(patientId),
                EncounterId(encounterId),
            )
        }
    }

    @Test
    fun `GET DocumentReference search returns an empty searchset bundle`() {
        val patientId = Uuid.generateV4()
        coEvery {
            documentReferenceService.searchDocumentReferences(PatientInputId(patientId), null)
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response = get("/fhir/DocumentReference?subject=Patient/$patientId")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"searchset\""))
            assertEquals(false, body.contains("\"entry\""))
        }
    }

    @Test
    fun `GET DocumentReference search rejects a token missing the DocumentReference scope`() {
        val patientId = Uuid.generateV4()

        testApp(scopes = emptySet(), boundPatient = patientId.toString()) {
            val response = get("/fhir/DocumentReference?subject=Patient/$patientId")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `GET DocumentReference search rejects a token bound to a different patient`() {
        val patientId = Uuid.generateV4()
        val otherPatientId = Uuid.generateV4().toString()

        testApp(boundPatient = otherPatientId) {
            val response = get("/fhir/DocumentReference?subject=Patient/$patientId")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    private fun createScope() = setOf(documentReferenceScope(setOf(Interaction.CREATE)))

    private fun validCreateBody(
        patientId: Uuid = Uuid.generateV4(),
        encounterId: Uuid = Uuid.generateV4(),
        clientSuppliedId: String? = null,
    ): String {
        val documentReference =
            DocumentReference(
                id = clientSuppliedId,
                status = Enumeration(value = DocumentReferenceStatus.Current),
                type =
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri(value = TYPE_SYSTEM),
                                    code = Code(value = TYPE_CODE),
                                )
                            )
                    ),
                subject = Reference(reference = FhirString(value = "Patient/$patientId")),
                context =
                    DocumentReference.Context(
                        encounter =
                            listOf(
                                Reference(reference = FhirString(value = "Encounter/$encounterId"))
                            )
                    ),
                content =
                    listOf(
                        DocumentReference.Content(
                            attachment = Attachment(contentType = Code(value = "application/pdf"))
                        )
                    ),
                description = FhirString(value = "Journalnotat tekst"),
            )
        return fhirJson.encodeToString(documentReference)
    }

    @Test
    fun `POST DocumentReference creates the resource with a server-generated id and returns 201 with Location`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        val generatedId = DocumentReferenceId(Uuid.generateV4())
        val persisted =
            sampleDocumentReference(
                generatedId.value.toString(),
                patientId.toString(),
                encounterId.toString(),
            )
        coEvery {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        } returns persisted

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(
                        validCreateBody(
                            patientId = patientId,
                            encounterId = encounterId,
                            clientSuppliedId = "client-chosen-id",
                        )
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(
                "$fhirServerUrl/DocumentReference/${generatedId.value}",
                response.headers[HttpHeaders.Location],
            )
            val body = response.bodyAsText()
            assertEquals(true, body.contains(generatedId.value.toString()))
            assertEquals(false, body.contains("client-chosen-id"))
        }
        coVerify(exactly = 1) {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        }
    }

    @Test
    fun `POST DocumentReference rejects a token missing the DocumentReference create scope`() {
        val patientId = Uuid.generateV4()

        testApp(scopes = emptySet(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
        coVerify(exactly = 0) {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        }
    }

    @Test
    fun `POST DocumentReference rejects a subject that does not match the launched patient and writes nothing`() {
        val patientId = Uuid.generateV4()
        val otherPatientId = Uuid.generateV4()

        testApp(scopes = createScope(), boundPatient = otherPatientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
        coVerify(exactly = 0) {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        }
    }

    @Test
    fun `POST DocumentReference rejects an encounter belonging to another patient and writes nothing`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        coEvery {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        } throws
            KonsultasjonTilhorerAnnenPasientException(
                KonsultasjonId(encounterId),
                PasientId(patientId),
            )

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId, encounterId = encounterId))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"OperationOutcome\""))
        }
    }

    @Test
    fun `POST DocumentReference rejects an unknown encounter and writes nothing`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        coEvery {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        } throws KonsultasjonNotFoundException(KonsultasjonId(encounterId))

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId, encounterId = encounterId))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `POST DocumentReference surfaces a duplicate id collision explicitly instead of a fallback success`() {
        val patientId = Uuid.generateV4()
        coEvery {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        } throws DuplikatJournalnotatException()

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId))
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }

    @Test
    fun `POST DocumentReference rejects invalid content with an OperationOutcome and writes nothing`() {
        val patientId = Uuid.generateV4()
        val invalidDocumentReference =
            DocumentReference(
                status = Enumeration(value = DocumentReferenceStatus.Current),
                type = null,
                subject = Reference(reference = FhirString(value = "Patient/$patientId")),
                content =
                    listOf(
                        DocumentReference.Content(
                            attachment = Attachment(contentType = Code(value = "application/pdf"))
                        )
                    ),
            )

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody(fhirJson.encodeToString(invalidDocumentReference))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"OperationOutcome\""))
            assertEquals(true, body.contains("\"issue\""))
        }
        coVerify(exactly = 0) {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        }
    }

    @Test
    fun `POST DocumentReference rejects malformed JSON with a structural OperationOutcome`() {
        val patientId = Uuid.generateV4()

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/DocumentReference") {
                    contentType(fhirContentType)
                    setBody("{ this is not valid json")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"OperationOutcome\""))
        }
        coVerify(exactly = 0) {
            documentReferenceService.createDocumentReference(any<OpprettJournalnotatRequest>())
        }
    }
}
