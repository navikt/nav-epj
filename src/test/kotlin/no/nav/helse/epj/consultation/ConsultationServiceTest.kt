package no.nav.helse.epj.consultation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationNotFoundForPatientException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.Test

class ConsultationServiceTest {

    private val consultationRepository = mockk<ConsultationRepository>()
    private val consultationService = ConsultationService(consultationRepository)

    @OptIn(ExperimentalUuidApi::class)
    private fun consultation(
        id: ConsultationId = ConsultationId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
        status: ConsultationStatus = ConsultationStatus.IN_PROGRESS,
    ) =
        Consultation(
            id = id,
            patientId = patientId,
            clinicId = ClinicId(Uuid.generateV4()),
            hpr = emptyList(),
            clinicalNotes = emptyList(),
            diagnoses = emptyList(),
            startedAt = java.time.LocalDateTime.now().minusHours(1),
            endedAt = null,
            status = status,
            reasonForVisit = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConsultation throws ConsultationNotFoundException when consultation does not exist`() =
        runTest {
            val consultationId = ConsultationId(Uuid.generateV4())
            coEvery {
                consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
            } returns null

            assertFailsWith<ConsultationNotFoundException> {
                consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConsultation returns consultation when it exists`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val forventet = consultation(id = consultationId)
        coEvery {
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        } returns forventet

        val resultat = consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER)

        assertSame(forventet, resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getOrCreateConsultation returns the same doctor's active consultation without creating a new one`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val hpr = PractitionerHpr("123")
            val activeConsultation = consultation(patientId = patientId)
            coEvery {
                consultationRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    hpr,
                    DEFAULT_ORG_NUMBER,
                )
            } returns activeConsultation

            val resultat =
                consultationService.getOrCreateConsultation(patientId, hpr, DEFAULT_ORG_NUMBER)

            assertSame(activeConsultation, resultat)
            coVerify(exactly = 0) { consultationRepository.insert(any()) }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getOrCreateConsultation creates a new consultation when none is active`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val hpr = PractitionerHpr("123")
        val opprettetId = ConsultationId(Uuid.generateV4())
        val createdConsultation = consultation(id = opprettetId, patientId = patientId)

        coEvery {
            consultationRepository.findActiveByPatientIdAndHpr(patientId, hpr, DEFAULT_ORG_NUMBER)
        } returns null
        coEvery { consultationRepository.insert(any()) } returns opprettetId
        coEvery {
            consultationRepository.findByConsultationId(opprettetId, DEFAULT_ORG_NUMBER)
        } returns createdConsultation

        val resultat =
            consultationService.getOrCreateConsultation(patientId, hpr, DEFAULT_ORG_NUMBER)

        assertSame(createdConsultation, resultat)
        coVerify(exactly = 1) { consultationRepository.insert(any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getOrCreateConsultation creates a new consultation for a different doctor even when one is active`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val annenLegeHpr = PractitionerHpr("456")
            val opprettetId = ConsultationId(Uuid.generateV4())
            val createdConsultation = consultation(id = opprettetId, patientId = patientId)

            coEvery {
                consultationRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    annenLegeHpr,
                    DEFAULT_ORG_NUMBER,
                )
            } returns null
            coEvery { consultationRepository.insert(any()) } returns opprettetId
            coEvery {
                consultationRepository.findByConsultationId(opprettetId, DEFAULT_ORG_NUMBER)
            } returns createdConsultation

            val resultat =
                consultationService.getOrCreateConsultation(
                    patientId,
                    annenLegeHpr,
                    DEFAULT_ORG_NUMBER,
                )

            assertSame(createdConsultation, resultat)
            coVerify(exactly = 1) { consultationRepository.insert(any()) }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createConsultation throws IllegalStateException when consultation does not exist after insert`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val opprettetId = ConsultationId(Uuid.generateV4())
            val createConsultation =
                CreateConsultation(
                    patientId = patientId,
                    hpr = emptyList(),
                    startedAt = LocalDateTime.now().minusHours(1),
                    status = ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            coEvery { consultationRepository.insert(createConsultation) } returns opprettetId
            coEvery {
                consultationRepository.findByConsultationId(opprettetId, DEFAULT_ORG_NUMBER)
            } returns null

            assertFailsWith<IllegalStateException> {
                consultationService.createConsultation(createConsultation)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `updateConsultation throws ConsultationNotFoundForPatientException when 0 rows are updated`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val request =
                UpdateConsultationRequest(
                    consultationId = ConsultationId(Uuid.generateV4()),
                    diagnoses = emptyList(),
                    clinicalNote = null,
                    finalize = false,
                )
            coEvery {
                consultationRepository.update(request, patientId, DEFAULT_ORG_NUMBER)
            } returns 0

            assertFailsWith<ConsultationNotFoundForPatientException> {
                consultationService.updateConsultation(request, patientId, DEFAULT_ORG_NUMBER)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `updateConsultation completes without error when rows are updated`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val request =
            UpdateConsultationRequest(
                consultationId = ConsultationId(Uuid.generateV4()),
                diagnoses = emptyList(),
                clinicalNote = "notat",
                finalize = true,
            )
        coEvery { consultationRepository.update(request, patientId, DEFAULT_ORG_NUMBER) } returns 2

        consultationService.updateConsultation(request, patientId, DEFAULT_ORG_NUMBER)

        coVerify(exactly = 1) {
            consultationRepository.update(request, patientId, DEFAULT_ORG_NUMBER)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `cancelConsultation throws ConsultationNotFoundForPatientException when 0 rows are updated`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val consultationId = ConsultationId(Uuid.generateV4())
            coEvery {
                consultationRepository.cancel(consultationId, patientId, DEFAULT_ORG_NUMBER)
            } returns 0

            assertFailsWith<ConsultationNotFoundForPatientException> {
                consultationService.cancelConsultation(
                    consultationId,
                    patientId,
                    DEFAULT_ORG_NUMBER,
                )
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `cancelConsultation completes without error when a row is updated`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery {
            consultationRepository.cancel(consultationId, patientId, DEFAULT_ORG_NUMBER)
        } returns 1

        consultationService.cancelConsultation(consultationId, patientId, DEFAULT_ORG_NUMBER)

        coVerify(exactly = 1) {
            consultationRepository.cancel(consultationId, patientId, DEFAULT_ORG_NUMBER)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getDiagnoses by consultationId returns diagnoses resolved from the katalog`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val diagnosis = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose")
        coEvery { consultationRepository.listDiagnoses(consultationId, DEFAULT_ORG_NUMBER) } returns
            listOf(diagnosis)

        val resultat = consultationService.getDiagnoses(consultationId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(diagnosis), resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertClinicalNote returns true when exactly one row is inserted`() = runTest {
        val clinicalNote =
            ClinicalNote(
                id = ClinicalNoteId(Uuid.generateV4()),
                consultationId = ConsultationId(Uuid.generateV4()),
                patientId = PatientId(Uuid.generateV4()),
                clinicalNote = "notat",
            )
        coEvery {
            consultationRepository.insertClinicalNote(clinicalNote, DEFAULT_ORG_NUMBER)
        } returns 1

        assertEquals(true, consultationService.insertClinicalNote(clinicalNote, DEFAULT_ORG_NUMBER))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertClinicalNote returns false when no row is inserted`() = runTest {
        val clinicalNote =
            ClinicalNote(
                id = ClinicalNoteId(Uuid.generateV4()),
                consultationId = ConsultationId(Uuid.generateV4()),
                patientId = PatientId(Uuid.generateV4()),
                clinicalNote = "notat",
            )
        coEvery {
            consultationRepository.insertClinicalNote(clinicalNote, DEFAULT_ORG_NUMBER)
        } returns 0

        assertEquals(
            false,
            consultationService.insertClinicalNote(clinicalNote, DEFAULT_ORG_NUMBER),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createClinicalNote assigns a server-generated id and delegates to the repository`() =
        runTest {
            val request =
                CreateClinicalNoteRequest(
                    patientId = PatientId(Uuid.generateV4()),
                    consultationId = ConsultationId(Uuid.generateV4()),
                    clinicalNote = "notat",
                )
            val slot = mutableListOf<ClinicalNoteId>()
            coEvery {
                consultationRepository.createClinicalNote(
                    capture(slot),
                    request,
                    DEFAULT_ORG_NUMBER,
                )
            } answers
                {
                    ClinicalNote(
                        id = slot.single(),
                        consultationId = request.consultationId,
                        patientId = request.patientId,
                        clinicalNote = request.clinicalNote,
                    )
                }

            val resultat = consultationService.createClinicalNote(request, DEFAULT_ORG_NUMBER)

            assertEquals(slot.single(), resultat.id)
            assertEquals(request.patientId, resultat.patientId)
            assertEquals(request.consultationId, resultat.consultationId)
            assertEquals(request.clinicalNote, resultat.clinicalNote)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getClinicalNotes delegates to the repository with the given patient and encounter filter`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val consultationId = ConsultationId(Uuid.generateV4())
            val clinicalNote =
                ClinicalNote(
                    id = ClinicalNoteId(Uuid.generateV4()),
                    consultationId = consultationId,
                    patientId = patientId,
                    clinicalNote = "notat",
                )
            coEvery {
                consultationRepository.listClinicalNotes(
                    patientId,
                    consultationId,
                    DEFAULT_ORG_NUMBER,
                )
            } returns listOf(clinicalNote)

            val resultat =
                consultationService.getClinicalNotes(patientId, consultationId, DEFAULT_ORG_NUMBER)

            assertEquals(listOf(clinicalNote), resultat)
        }
}
