package no.nav.helse.epj.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class ConsultationRepositoryClinicalNoteTest : ConsultationRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createClinicalNote stores a complete round trip of a clinical note`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val consultationId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        val id = ClinicalNoteId(Uuid.generateV4())
        val request =
            CreateClinicalNoteRequest(
                patientId = patientId,
                consultationId = consultationId,
                clinicalNote = "notat",
            )

        val opprettet = consultationRepository.createClinicalNote(id, request, DEFAULT_ORG_NUMBER)

        assertEquals(id, opprettet.id)
        assertEquals(patientId, opprettet.patientId)
        assertEquals(consultationId, opprettet.consultationId)
        assertEquals("notat", opprettet.clinicalNote)
        val lagret = consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)
        assertEquals("notat", lagret?.clinicalNote)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createClinicalNote throws ConsultationNotFoundException for an unknown encounter`() =
        runTest {
            val patientId = createPatient()
            val request =
                CreateClinicalNoteRequest(
                    patientId = patientId,
                    consultationId = ConsultationId(Uuid.generateV4()),
                    clinicalNote = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.ConsultationNotFoundException> {
                consultationRepository.createClinicalNote(
                    ClinicalNoteId(Uuid.generateV4()),
                    request,
                    DEFAULT_ORG_NUMBER,
                )
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createClinicalNote throws ConsultationBelongsToOtherPatientException when the encounter belongs to another patient`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val eier = createPatient(hpr = hpr)
            val consultationId =
                consultationRepository.insert(
                    CreateConsultation(
                        eier,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val otherPatient = createPatient(hpr = hpr)
            val request =
                CreateClinicalNoteRequest(
                    patientId = otherPatient,
                    consultationId = consultationId,
                    clinicalNote = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException> {
                consultationRepository.createClinicalNote(
                    ClinicalNoteId(Uuid.generateV4()),
                    request,
                    DEFAULT_ORG_NUMBER,
                )
            }
            assertNull(
                consultationRepository.findClinicalNote(
                    ClinicalNoteId(Uuid.generateV4()),
                    DEFAULT_ORG_NUMBER,
                )
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createClinicalNote with a colliding id throws DuplicateClinicalNoteException and leaves data unchanged`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            val consultationId =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val id = ClinicalNoteId(Uuid.generateV4())
            val request =
                CreateClinicalNoteRequest(
                    patientId = patientId,
                    consultationId = consultationId,
                    clinicalNote = "original",
                )
            consultationRepository.createClinicalNote(id, request, DEFAULT_ORG_NUMBER)

            assertFailsWith<no.nav.helse.core.utils.DuplicateClinicalNoteException> {
                consultationRepository.createClinicalNote(
                    id,
                    request.copy(clinicalNote = "endret"),
                    DEFAULT_ORG_NUMBER,
                )
            }

            assertEquals(
                "original",
                consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)?.clinicalNote,
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listClinicalNotes by patientId only returns clinical notes for the correct patient`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientA = createPatient(hpr = hpr)
            val patientB = createPatient(hpr = hpr)
            val consultationA =
                consultationRepository.insert(
                    CreateConsultation(
                        patientA,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val consultationB =
                consultationRepository.insert(
                    CreateConsultation(
                        patientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            consultationRepository.createClinicalNote(
                ClinicalNoteId(Uuid.generateV4()),
                CreateClinicalNoteRequest(patientA, consultationA, "for A"),
                DEFAULT_ORG_NUMBER,
            )
            consultationRepository.createClinicalNote(
                ClinicalNoteId(Uuid.generateV4()),
                CreateClinicalNoteRequest(patientB, consultationB, "for B"),
                DEFAULT_ORG_NUMBER,
            )

            val notaterForA =
                consultationRepository.listClinicalNotes(patientA, null, DEFAULT_ORG_NUMBER)

            assertEquals(1, notaterForA.size)
            assertEquals("for A", notaterForA.single().clinicalNote)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listClinicalNotes filters by encounter when both patient and encounter are given`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            val consultationA =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val consultationB =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            consultationRepository.createClinicalNote(
                ClinicalNoteId(Uuid.generateV4()),
                CreateClinicalNoteRequest(patientId, consultationA, "for A"),
                DEFAULT_ORG_NUMBER,
            )
            consultationRepository.createClinicalNote(
                ClinicalNoteId(Uuid.generateV4()),
                CreateClinicalNoteRequest(patientId, consultationB, "for B"),
                DEFAULT_ORG_NUMBER,
            )

            val notater =
                consultationRepository.listClinicalNotes(
                    patientId,
                    consultationA,
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(1, notater.size)
            assertEquals("for A", notater.single().clinicalNote)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listClinicalNotes returns an empty list when the patient has no clinical notes`() =
        runTest {
            val patientId = createPatient()

            val notater =
                consultationRepository.listClinicalNotes(patientId, null, DEFAULT_ORG_NUMBER)

            assertTrue(notater.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertClinicalNote rejects an encounter that belongs to another patient`() = runTest {
        val eier = createPatient()
        val consultationId = createConsultation(eier)
        val otherPatient = createPatient()
        val id = ClinicalNoteId(Uuid.generateV4())

        assertFailsWith<no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException> {
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, consultationId, otherPatient, "notat"),
                DEFAULT_ORG_NUMBER,
            )
        }
        assertNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertClinicalNote does not move an existing clinical note to another consultation`() =
        runTest {
            val eier = createPatient()
            val ownerConsultation = createConsultation(eier)
            val id = ClinicalNoteId(Uuid.generateV4())
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, ownerConsultation, eier, "original"),
                DEFAULT_ORG_NUMBER,
            )
            val angriper = createPatient()
            val attackerConsultation = createConsultation(angriper)

            val rows =
                consultationRepository.insertClinicalNote(
                    ClinicalNote(id, attackerConsultation, angriper, "overskrevet"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(0, rows)
            val lagret = consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)
            assertEquals("original", lagret?.clinicalNote)
            assertEquals(eier, lagret?.patientId)
            assertEquals(ownerConsultation, lagret?.consultationId)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertClinicalNote updates the text of an existing clinical note in the same consultation`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val id = ClinicalNoteId(Uuid.generateV4())
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, consultationId, patientId, "første"),
                DEFAULT_ORG_NUMBER,
            )

            val rows =
                consultationRepository.insertClinicalNote(
                    ClinicalNote(id, consultationId, patientId, "andre"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(1, rows)
            assertEquals(
                "andre",
                consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)?.clinicalNote,
            )
        }
}
