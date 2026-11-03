package no.nav.helse.fhir.documentreference

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.consultation.ClinicalNote
import no.nav.helse.epj.consultation.ClinicalNoteId
import no.nav.helse.epj.consultation.Consultation
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.consultation.ConsultationService
import no.nav.helse.epj.consultation.CreateClinicalNoteRequest
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class DocumentReferenceServiceTest {

    private val consultationService = mockk<ConsultationService>()
    private val documentReferenceService = DocumentReferenceService(consultationService)

    @OptIn(ExperimentalUuidApi::class)
    private fun clinicalNote(
        id: ClinicalNoteId = ClinicalNoteId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
        consultationId: ConsultationId = ConsultationId(Uuid.generateV4()),
        clinicalNote: String? = "notat",
    ) =
        ClinicalNote(
            id = id,
            consultationId = consultationId,
            patientId = patientId,
            clinicalNote = clinicalNote,
        )

    @OptIn(ExperimentalUuidApi::class)
    private fun consultation(persisted: ClinicalNote, hpr: List<String>) =
        Consultation(
            id = persisted.consultationId,
            patientId = persisted.patientId,
            clinicId = ClinicId(Uuid.generateV4()),
            hpr = hpr,
            clinicalNotes = emptyList(),
            diagnoses = emptyList(),
            startedAt = java.time.LocalDateTime.of(2025, 1, 1, 10, 0),
            endedAt = null,
            status = ConsultationStatus.IN_PROGRESS,
            reasonForVisit = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createDocumentReference persists through ConsultationService and maps the server-generated id back`() =
        runTest {
            val request =
                CreateClinicalNoteRequest(
                    patientId = PatientId(Uuid.generateV4()),
                    consultationId = ConsultationId(Uuid.generateV4()),
                    clinicalNote = "notat",
                )
            val persisted =
                clinicalNote(
                    patientId = request.patientId,
                    consultationId = request.consultationId,
                    clinicalNote = request.clinicalNote,
                )
            coEvery { consultationService.createClinicalNote(request, DEFAULT_ORG_NUMBER) } returns
                persisted
            coEvery {
                consultationService.getConsultation(persisted.consultationId, DEFAULT_ORG_NUMBER)
            } returns consultation(persisted, emptyList())

            val documentReference =
                documentReferenceService.createDocumentReference(request, DEFAULT_ORG_NUMBER)

            assertEquals(persisted.id.value.toString(), documentReference.id)
            assertEquals(
                "Patient/${request.patientId.value}",
                documentReference.subject?.reference?.value,
            )
            assertEquals(
                "Encounter/${request.consultationId.value}",
                documentReference.context?.encounter?.single()?.reference?.value,
            )
            assertEquals("notat", documentReference.description?.value)
            coVerify(exactly = 1) {
                consultationService.createClinicalNote(request, DEFAULT_ORG_NUMBER)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createDocumentReference propagates encounter-not-found without swallowing the error`() =
        runTest {
            val request =
                CreateClinicalNoteRequest(
                    patientId = PatientId(Uuid.generateV4()),
                    consultationId = ConsultationId(Uuid.generateV4()),
                    clinicalNote = "notat",
                )
            coEvery { consultationService.createClinicalNote(request, DEFAULT_ORG_NUMBER) } throws
                ConsultationNotFoundException(request.consultationId)

            assertFailsWith<ConsultationNotFoundException> {
                documentReferenceService.createDocumentReference(request, DEFAULT_ORG_NUMBER)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createDocumentReference maps author from the helsepersonell tied to the patient`() =
        runTest {
            val request =
                CreateClinicalNoteRequest(
                    patientId = PatientId(Uuid.generateV4()),
                    consultationId = ConsultationId(Uuid.generateV4()),
                    clinicalNote = "notat",
                )
            val persisted =
                clinicalNote(patientId = request.patientId, consultationId = request.consultationId)
            coEvery { consultationService.createClinicalNote(request, DEFAULT_ORG_NUMBER) } returns
                persisted
            coEvery {
                consultationService.getConsultation(persisted.consultationId, DEFAULT_ORG_NUMBER)
            } returns consultation(persisted, listOf("999"))

            val documentReference =
                documentReferenceService.createDocumentReference(request, DEFAULT_ORG_NUMBER)

            assertEquals("Practitioner/999", documentReference.author.single().reference?.value)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getDocumentReferences returns null when the clinical note does not exist`() = runTest {
        val id = DocumentReferenceId(Uuid.generateV4())
        coEvery {
            consultationService.getClinicalNote(ClinicalNoteId(id.value), DEFAULT_ORG_NUMBER)
        } returns null

        assertNull(documentReferenceService.getDocumentReferences(id, DEFAULT_ORG_NUMBER))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchDocumentReferences returns an empty searchset bundle when the patient has no notater`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery {
                consultationService.getClinicalNotes(patientId, null, DEFAULT_ORG_NUMBER)
            } returns emptyList()
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                emptyList()

            val bundle =
                documentReferenceService.searchDocumentReferences(
                    patientId = PatientInputId(patientId.value),
                    encounterId = null,
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(com.google.fhir.model.r4.Bundle.BundleType.Searchset, bundle.type.value)
            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchDocumentReferences returns every notat for the patient without an encounter filter`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val forste = clinicalNote(patientId = patientId)
            val andre = clinicalNote(patientId = patientId)
            coEvery {
                consultationService.getClinicalNotes(patientId, null, DEFAULT_ORG_NUMBER)
            } returns listOf(forste, andre)
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                emptyList()

            val bundle =
                documentReferenceService.searchDocumentReferences(
                    patientId = PatientInputId(patientId.value),
                    encounterId = null,
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(
                setOf(forste.id.value.toString(), andre.id.value.toString()),
                bundle.entry
                    .map { (it.resource as com.google.fhir.model.r4.DocumentReference).id }
                    .toSet(),
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchDocumentReferences passes the encounter filter through to ConsultationService`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val consultationId = ConsultationId(Uuid.generateV4())
            val notat = clinicalNote(patientId = patientId, consultationId = consultationId)
            coEvery {
                consultationService.getClinicalNotes(patientId, consultationId, DEFAULT_ORG_NUMBER)
            } returns listOf(notat)
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                emptyList()

            val bundle =
                documentReferenceService.searchDocumentReferences(
                    patientId = PatientInputId(patientId.value),
                    encounterId = EncounterId(consultationId.value),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(
                notat.id.value.toString(),
                (bundle.entry.single().resource as com.google.fhir.model.r4.DocumentReference).id,
            )
            coVerify(exactly = 1) {
                consultationService.getClinicalNotes(patientId, consultationId, DEFAULT_ORG_NUMBER)
            }
        }
}
