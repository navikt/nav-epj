@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.QuestionnaireResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.ehr.consultation.ClinicalNote
import no.nav.helse.ehr.consultation.ClinicalNoteId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class ClinicalDocumentAuthorTest : FhirWriteBackTestSupport() {

    private fun authors(resource: DocumentReference) = resource.author.map { it.reference?.value }

    private suspend fun HttpClient.readAuthors(
        id: Uuid,
        patientId: String,
    ): Pair<List<String?>, List<String?>> {
        val read = decodeWire(get("/fhir/DocumentReference/$id").bodyAsText()) as DocumentReference
        val search =
            decodeWire(get("/fhir/DocumentReference?patient=Patient/$patientId").bodyAsText())
                as Bundle
        val searched =
            search.entry.map { it.resource as DocumentReference }.single { it.id == id.toString() }
        return authors(read) to authors(searched)
    }

    @Test
    fun `the persisted document authors are returned and not every practitioner on the consultation`() {
        val (patientId, consultationId) =
            runBlocking {
                val patient = createPatient()
                patient to createConsultation(patient, hprs = listOf("123", "456"))
            }
        val id = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId, author = "456"),
            )
            assertEquals(
                listOf("Practitioner/456") to listOf("Practitioner/456"),
                readAuthors(id, patientId.value.toString()),
            )

            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId, author = null),
            )
            assertEquals(
                emptyList<String>() to emptyList<String>(),
                readAuthors(id, patientId.value.toString()),
            )

            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId, author = "123"),
            )
            assertEquals(
                listOf("Practitioner/123") to listOf("Practitioner/123"),
                readAuthors(id, patientId.value.toString()),
            )
        }
    }

    @Test
    fun `a document created without an author is stored without one`() {
        val (patientId, consultationId) =
            runBlocking {
                val patient = createPatient()
                patient to createConsultation(patient, hprs = listOf("123", "456"))
            }
        val id = Uuid.generateV4()
        testApp {
            val created =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(id.toString(), patientId, consultationId, author = null),
                )

            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals(
                emptyList<String>() to emptyList<String>(),
                readAuthors(id, patientId.value.toString()),
            )
        }
    }

    @Test
    fun `an EHR note is read with the practitioners of its consultation`() {
        val (patientId, consultationId) =
            runBlocking {
                val patient = createPatient()
                patient to createConsultation(patient, hprs = listOf("123", "456"))
            }
        val id = ClinicalNoteId(Uuid.generateV4())
        runBlocking {
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, consultationId, patientId, "egen tekst"),
                DEFAULT_ORG_NUMBER,
            )
        }
        testApp {
            assertEquals(
                setOf("Practitioner/123", "Practitioner/456"),
                readAuthors(id.value, patientId.value.toString()).first.toSet(),
            )
        }
    }

    @Test
    fun `a QuestionnaireResponse keeps authors that are not practitioners`() {
        val (patientId, consultationId) = runBlocking { consultationFor() }
        val relatedPerson = "RelatedPerson/${Uuid.generateV4()}"
        val authors =
            listOf(
                "{\"reference\":\"Patient/${patientId.value}\",\"display\":\"Pasienten\"}" to
                    listOf("Patient/${patientId.value}", "Pasienten", null),
                "{\"reference\":\"$relatedPerson\"}" to listOf(relatedPerson, null, null),
                "{\"display\":\"Helseapp\"}" to listOf(null, "Helseapp", null),
                "{\"identifier\":{\"system\":\"urn:oid:1.2.3\",\"value\":\"abc\"}}" to
                    listOf(null, null, "abc"),
            )
        testApp {
            for ((author, expected) in authors) {
                val id = Uuid.generateV4()

                val created =
                    putJson(
                        "/fhir/QuestionnaireResponse/$id",
                        questionnaireResponse(
                            id.toString(),
                            patientId,
                            consultationId,
                            author = author,
                        ),
                    )
                val stored =
                    (decodeWire(get("/fhir/QuestionnaireResponse/$id").bodyAsText())
                            as QuestionnaireResponse)
                        .author

                assertEquals(HttpStatusCode.Created, created.status, author)
                assertEquals(
                    expected,
                    listOf(
                        stored?.reference?.value,
                        stored?.display?.value,
                        stored?.identifier?.value?.value,
                    ),
                )
            }
        }
    }

    @Test
    fun `a QuestionnaireResponse practitioner author must be on the consultation`() {
        val (patientId, consultationId) = runBlocking { consultationFor() }
        val id = Uuid.generateV4()
        testApp {
            val response =
                putJson(
                    "/fhir/QuestionnaireResponse/$id",
                    questionnaireResponse(
                        id.toString(),
                        patientId,
                        consultationId,
                        author = "{\"reference\":\"Practitioner/999\"}",
                    ),
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(get("/fhir/QuestionnaireResponse/$id").status == HttpStatusCode.NotFound)
        }
    }
}
