package no.nav.helse.epj.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.InvalidDiagnosisException
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.Test

class ConsultationRepositoryUpdateTest : ConsultationRepositoryTestBase() {
    @Test
    fun `update returns 0 rows and makes no changes when patientId does not own the consultation`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            val otherPatientId = createPatient(hpr = hpr)
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

            val updatedRows =
                consultationRepository.update(
                    UpdateConsultationRequest(
                        consultationId = consultationId,
                        diagnoses =
                            listOf(
                                CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)
                            ),
                        journalNotat = "notat",
                        finalize = true,
                    ),
                    otherPatientId,
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(0, updatedRows)
        }

    @Test
    fun `update adds a new journalnotat, stores diagnosis and completes consultation`() = runTest {
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
        val updatedRows =
            consultationRepository.update(
                UpdateConsultationRequest(
                    consultationId = consultationId,
                    diagnoses =
                        listOf(CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)),
                    journalNotat = "oppdatert notat",
                    finalize = true,
                ),
                patientId,
                DEFAULT_ORG_NUMBER,
            )

        // 1 for ny diagnose + 1 for nytt journalnotat (insert) + 1 for ferdigstilt konsultasjon
        assertEquals(3, updatedRows)
        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(ConsultationStatus.FINISHED, consultation.status)
        assertNotNull(consultation.endedAt)
        assertEquals(1, consultation.journalnotat.size)
        assertEquals("oppdatert notat", consultation.journalnotat.last().journalnotat)
        assertEquals(1, consultation.diagnoses.size)
        val savedDiagnosis = consultation.diagnoses.single()
        assertEquals(DiagnoseType.ICPC2, savedDiagnosis.system)
        assertEquals("A01", savedDiagnosis.code)
        assertEquals(Diagnose.from(DiagnoseType.ICPC2, "A01")!!.text, savedDiagnosis.text)
    }

    @Test
    fun `update removes a diagnosis that is left out of a later diagnoses list`() = runTest {
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

        consultationRepository.update(
            UpdateConsultationRequest(
                consultationId = consultationId,
                diagnoses =
                    listOf(
                        CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2),
                        CreateDiagnosisRequest(code = "A02", system = DiagnoseType.ICPC2),
                    ),
                journalNotat = null,
                finalize = false,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        consultationRepository.update(
            UpdateConsultationRequest(
                consultationId = consultationId,
                diagnoses =
                    listOf(CreateDiagnosisRequest(code = "A02", system = DiagnoseType.ICPC2)),
                journalNotat = null,
                finalize = false,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        val remainingDiagnosis = consultation.diagnoses.single()
        assertEquals("A02", remainingDiagnosis.code)
    }

    @Test
    fun `update removes all diagnoses when the diagnoses list is submitted empty`() = runTest {
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

        consultationRepository.update(
            UpdateConsultationRequest(
                consultationId = consultationId,
                diagnoses =
                    listOf(CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)),
                journalNotat = null,
                finalize = false,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        consultationRepository.update(
            UpdateConsultationRequest(
                consultationId = consultationId,
                diagnoses = emptyList(),
                journalNotat = null,
                finalize = false,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(emptyList(), consultation.diagnoses)
    }

    @Test
    fun `update leaves diagnoses unchanged when an unknown diagnosisCode is rejected`() = runTest {
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

        consultationRepository.update(
            UpdateConsultationRequest(
                consultationId = consultationId,
                diagnoses =
                    listOf(CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)),
                journalNotat = null,
                finalize = false,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        assertFailsWith<InvalidDiagnosisException> {
            consultationRepository.update(
                UpdateConsultationRequest(
                    consultationId = consultationId,
                    diagnoses =
                        listOf(
                            CreateDiagnosisRequest(
                                code = "IKKE-EN-GYLDIG-KODE",
                                system = DiagnoseType.ICPC2,
                            )
                        ),
                    journalNotat = null,
                    finalize = false,
                ),
                patientId,
                DEFAULT_ORG_NUMBER,
            )
        }

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals("A01", consultation.diagnoses.single().code)
    }

    @Test
    fun `update leaves the consultation open when finalize is false`() = runTest {
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

        consultationRepository.update(
            UpdateConsultationRequest(consultationId, emptyList(), "notat", finalize = false),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(ConsultationStatus.IN_PROGRESS, consultation.status)
        assertNull(consultation.endedAt)
    }

    @Test
    fun `cancel marks an ongoing consultation as CANCELLED`() = runTest {
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

        val updatedRows =
            consultationRepository.cancel(consultationId, patientId, DEFAULT_ORG_NUMBER)

        assertEquals(1, updatedRows)
        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(ConsultationStatus.CANCELLED, consultation.status)
        assertNotNull(consultation.endedAt)
    }

    @Test
    fun `cancel returns 0 rows and makes no changes when patientId does not own the consultation`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            val otherPatientId = createPatient(hpr = hpr)
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

            val updatedRows =
                consultationRepository.cancel(consultationId, otherPatientId, DEFAULT_ORG_NUMBER)

            assertEquals(0, updatedRows)
            val consultation =
                consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
            assertNotNull(consultation)
            assertEquals(ConsultationStatus.IN_PROGRESS, consultation.status)
        }

    @Test
    fun `cancel does not reopen an already completed consultation`() = runTest {
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
        consultationRepository.update(
            UpdateConsultationRequest(consultationId, emptyList(), null, finalize = true),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        val updatedRows =
            consultationRepository.cancel(consultationId, patientId, DEFAULT_ORG_NUMBER)

        assertEquals(0, updatedRows)
        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(ConsultationStatus.FINISHED, consultation.status)
    }

    @Test
    fun `update saving the same journalnotat text twice does not create a duplicate row`() =
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
            val request =
                UpdateConsultationRequest(consultationId, emptyList(), "notat", finalize = false)

            consultationRepository.update(request, patientId, DEFAULT_ORG_NUMBER)
            consultationRepository.update(request, patientId, DEFAULT_ORG_NUMBER)

            val consultation =
                consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
            assertNotNull(consultation)
            assertEquals(1, consultation.journalnotat.size)
            assertEquals("notat", consultation.journalnotat.single().journalnotat)
        }

    @Test
    fun `update saving a changed journalnotat text updates the existing row instead of adding one`() =
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

            consultationRepository.update(
                UpdateConsultationRequest(
                    consultationId,
                    emptyList(),
                    "første notat",
                    finalize = false,
                ),
                patientId,
                DEFAULT_ORG_NUMBER,
            )
            consultationRepository.update(
                UpdateConsultationRequest(
                    consultationId,
                    emptyList(),
                    "oppdatert notat",
                    finalize = false,
                ),
                patientId,
                DEFAULT_ORG_NUMBER,
            )

            val consultation =
                consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
            assertNotNull(consultation)
            assertEquals(1, consultation.journalnotat.size)
            assertEquals("oppdatert notat", consultation.journalnotat.single().journalnotat)
        }
}
