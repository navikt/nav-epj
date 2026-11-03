package no.nav.helse.epj.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.ConsultationDiagnosisCodeTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.InvalidDiagnosisException
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test

class ConsultationRepositoryDiagnosisTest : ConsultationRepositoryTestBase() {
    @Test
    fun `consultation retrieves an empty list of diagnoses when there are none`() = runTest {
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

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)

        assertEquals(emptyList(), consultation?.diagnoses)
    }

    @Test
    fun `update throws InvalidDiagnosisException for an unknown diagnosis code`() = runTest {
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
                    clinicalNote = "notat",
                    finalize = false,
                ),
                patientId,
                DEFAULT_ORG_NUMBER,
            )
        }
    }

    @Test
    fun `updateDiagnosis does not store the same diagnosis twice on the same consultation`() =
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
            val diagnosis = CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)

            val firstInsert =
                consultationRepository.updateDiagnosis(diagnosis, consultationId.value)
            val secondInsert =
                consultationRepository.updateDiagnosis(diagnosis, consultationId.value)

            assertEquals(1, firstInsert)
            assertEquals(0, secondInsert)
            val diagnosisCount = dbQuery {
                ConsultationDiagnosisCodeTable.selectAll()
                    .where { ConsultationDiagnosisCodeTable.consultationId eq consultationId.value }
                    .count()
            }
            assertEquals(1, diagnosisCount)
        }

    @Test
    fun `updateDiagnosis stores the katalog's canonical ICD10 code regardless of dot formatting`() =
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

            val withoutPeriod =
                consultationRepository.updateDiagnosis(
                    CreateDiagnosisRequest(code = "A000", system = DiagnoseType.ICD10),
                    consultationId.value,
                )
            val withPeriod =
                consultationRepository.updateDiagnosis(
                    CreateDiagnosisRequest(code = "A00.0", system = DiagnoseType.ICD10),
                    consultationId.value,
                )

            assertEquals(1, withoutPeriod)
            assertEquals(0, withPeriod)
            val savedDiagnoses =
                consultationRepository.listDiagnoses(consultationId, DEFAULT_ORG_NUMBER)
            assertEquals(1, savedDiagnoses.size)
            assertEquals("A00.0", savedDiagnoses.single().code)
        }

    @Test
    fun `updateDiagnosis stores the same diagnosis on different consultations`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)

        val consultationId1 =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val consultationId2 =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val diagnosis = CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)

        val firstInsert = consultationRepository.updateDiagnosis(diagnosis, consultationId1.value)

        val secondInsert = consultationRepository.updateDiagnosis(diagnosis, consultationId2.value)

        assertEquals(1, firstInsert)
        assertEquals(1, secondInsert)

        val savedDiagnoses = consultationRepository.listDiagnoses(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(2, savedDiagnoses.size)
    }

    @Test
    fun `listDiagnoses by patientId only includes diagnoses for consultations owned by that patient`() =
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
            consultationRepository.updateDiagnosis(
                CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2),
                consultationA.value,
            )
            consultationRepository.updateDiagnosis(
                CreateDiagnosisRequest(code = "A02", system = DiagnoseType.ICPC2),
                consultationB.value,
            )

            val diagnosesForA = consultationRepository.listDiagnoses(patientA, DEFAULT_ORG_NUMBER)

            assertEquals(1, diagnosesForA.size)
            assertEquals("A01", diagnosesForA.single().code)
        }

    @Test
    fun `listDiagnoses resolves official catalogue text for both patient and consultation lookups`() =
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
            val diagnosis = CreateDiagnosisRequest(code = "A01", system = DiagnoseType.ICPC2)

            consultationRepository.updateDiagnosis(diagnosis, consultationId.value)

            val expectedText = Diagnose.from(DiagnoseType.ICPC2, "A01")!!.text
            val diagnosesByPatient =
                consultationRepository.listDiagnoses(patientId, DEFAULT_ORG_NUMBER)
            val diagnosesByConsultation =
                consultationRepository.listDiagnoses(consultationId, DEFAULT_ORG_NUMBER)

            assertEquals(expectedText, diagnosesByPatient.single().text)
            assertEquals(expectedText, diagnosesByConsultation.single().text)
        }
}
