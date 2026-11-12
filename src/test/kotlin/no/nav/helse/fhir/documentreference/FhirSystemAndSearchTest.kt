@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.DocumentReference
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.ehr.consultation.ClinicalNoteId
import no.nav.helse.fhir.FhirWriteBackTestSupport
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.ensureOrg
import org.junit.Test

class FhirSystemAndSearchTest : FhirWriteBackTestSupport() {

    private val systemScopes =
        setOf(
            scope(
                "DocumentReference",
                *Interaction.entries.toTypedArray(),
                context = ScopeContext.SYSTEM,
            ),
            scope(
                "QuestionnaireResponse",
                *Interaction.entries.toTypedArray(),
                context = ScopeContext.SYSTEM,
            ),
        )

    private class Clinics(
        val patientA: no.nav.helse.ehr.patient.PatientId,
        val consultationA: no.nav.helse.ehr.consultation.ConsultationId,
        val patientB: no.nav.helse.ehr.patient.PatientId,
        val consultationB: no.nav.helse.ehr.consultation.ConsultationId,
    )

    private fun twoClinics(): Clinics = runBlocking {
        ensureOrg(OTHER_ORG_NUMBER)
        val patientA = createPatient()
        val patientB = createPatient()
        Clinics(
            patientA,
            createConsultation(patientA),
            patientB,
            createConsultation(patientB, orgNumber = OTHER_ORG_NUMBER),
        )
    }

    @Test
    fun `a system token writes and reads documents across clinics`() {
        val clinics = twoClinics()
        val first = Uuid.generateV4()
        val second = Uuid.generateV4()
        testApp(systemScopes, orgNumber = null) {
            val a =
                putJson(
                    "/fhir/DocumentReference/$first",
                    documentReference(first.toString(), clinics.patientA, clinics.consultationA),
                )
            val b =
                putJson(
                    "/fhir/DocumentReference/$second",
                    documentReference(second.toString(), clinics.patientB, clinics.consultationB),
                )

            assertEquals(HttpStatusCode.Created, a.status)
            assertEquals(HttpStatusCode.Created, b.status)
            assertEquals(HttpStatusCode.OK, get("/fhir/DocumentReference/$second").status)
        }
        runBlocking {
            assertNotNull(
                consultationRepository.findClinicalNote(ClinicalNoteId(first), DEFAULT_ORG_NUMBER)
            )
            assertNotNull(
                consultationRepository.findClinicalNote(ClinicalNoteId(second), OTHER_ORG_NUMBER)
            )
        }
    }

    @Test
    fun `a clinician token cannot read or overwrite a document of another clinic`() {
        val clinics = twoClinics()
        val id = Uuid.generateV4()
        testApp(systemScopes, orgNumber = null) {
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), clinics.patientB, clinics.consultationB),
            )
        }
        testApp(orgNumber = DEFAULT_ORG_NUMBER) {
            val overwrite =
                putJson(
                    "/fhir/DocumentReference/$id",
                    documentReference(
                        id.toString(),
                        clinics.patientB,
                        clinics.consultationB,
                        title = "x.pdf",
                    ),
                )

            assertEquals(HttpStatusCode.BadRequest, overwrite.status)
            assertEquals(HttpStatusCode.NotFound, get("/fhir/DocumentReference/$id").status)
            assertEquals(HttpStatusCode.NotFound, delete("/fhir/DocumentReference/$id").status)
        }
        testApp(orgNumber = OTHER_ORG_NUMBER) {
            assertEquals(HttpStatusCode.OK, get("/fhir/DocumentReference/$id").status)
        }
    }

    @Test
    fun `a system batch stores documents and responses across clinics`() {
        val clinics = twoClinics()
        val first = Uuid.generateV4()
        val second = Uuid.generateV4()
        val questionnaireResponse = Uuid.generateV4()
        testApp(systemScopes, orgNumber = null) {
            val response =
                postJson(
                    "/fhir",
                    batch(
                        entry(
                            "PUT",
                            "DocumentReference/$first",
                            documentReference(
                                first.toString(),
                                clinics.patientA,
                                clinics.consultationA,
                            ),
                        ),
                        entry(
                            "PUT",
                            "DocumentReference/$second",
                            documentReference(
                                second.toString(),
                                clinics.patientB,
                                clinics.consultationB,
                            ),
                        ),
                        entry(
                            "PUT",
                            "QuestionnaireResponse/$questionnaireResponse",
                            questionnaireResponse(
                                questionnaireResponse.toString(),
                                clinics.patientB,
                                clinics.consultationB,
                            ),
                        ),
                    ),
                )

            val bundle = decodeWire(response.bodyAsText()) as Bundle
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                listOf("201 Created", "201 Created", "201 Created"),
                bundle.entry.map { it.response?.status?.value },
            )
        }
        runBlocking {
            assertNotNull(
                consultationRepository.findClinicalNote(ClinicalNoteId(second), OTHER_ORG_NUMBER)
            )
        }
    }

    @Test
    fun `search filters on encounter and related questionnaire response`() {
        val (patientId, firstConsultation) = runBlocking { consultationFor() }
        val secondConsultation = runBlocking { createConsultation(patientId) }
        val related = Uuid.generateV4()
        val plain = Uuid.generateV4()
        val linked = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/DocumentReference/$plain",
                documentReference(plain.toString(), patientId, firstConsultation),
            )
            putJson(
                "/fhir/DocumentReference/$linked",
                documentReference(
                    linked.toString(),
                    patientId,
                    secondConsultation,
                    related = related,
                ),
            )
            val patient = "patient=Patient/${patientId.value}"

            fun ids(bundle: String) =
                (decodeWire(bundle) as Bundle).entry.map { (it.resource as DocumentReference).id }

            assertEquals(
                setOf(plain.toString(), linked.toString()),
                ids(get("/fhir/DocumentReference?$patient").bodyAsText()).toSet(),
            )
            assertEquals(
                listOf(plain.toString()),
                ids(
                    get(
                            "/fhir/DocumentReference?$patient&encounter=Encounter/${firstConsultation.value}"
                        )
                        .bodyAsText()
                ),
            )
            assertEquals(
                listOf(linked.toString()),
                ids(
                    get("/fhir/DocumentReference?$patient&related=QuestionnaireResponse/$related")
                        .bodyAsText()
                ),
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                get("/fhir/DocumentReference?$patient&related=not-a-reference").status,
            )
        }
    }

    @Test
    fun `search is limited by scope, patient and organization`() {
        val (patientId, consultationId) = runBlocking { consultationFor() }
        val id = Uuid.generateV4()
        testApp {
            putJson(
                "/fhir/DocumentReference/$id",
                documentReference(id.toString(), patientId, consultationId),
            )
        }
        val search = "/fhir/DocumentReference?patient=Patient/${patientId.value}"
        testApp(setOf(scope("DocumentReference", Interaction.READ))) {
            assertEquals(HttpStatusCode.Forbidden, get(search).status)
        }
        testApp(allWritesForPatient, boundPatient = Uuid.generateV4().toString()) {
            assertEquals(HttpStatusCode.NotFound, get(search).status)
        }
        runBlocking { ensureOrg(OTHER_ORG_NUMBER) }
        testApp(orgNumber = OTHER_ORG_NUMBER) {
            val bundle = decodeWire(get(search).bodyAsText()) as Bundle
            assertEquals(0, bundle.entry.size)
        }
    }

    @Test
    fun `a malformed JSON body is rejected`() {
        val id = Uuid.generateV4()
        testApp {
            val response = putJson("/fhir/DocumentReference/$id", "{\"resourceType\":\"DocumentRef")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
