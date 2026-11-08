package no.nav.helse.ehr.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.helse.core.db.ConsultationDiagnosisCodeTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.DuplicateDiagnosisException
import no.nav.helse.core.utils.InvalidDiagnosisException
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
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

    private suspend fun saveDiagnoses(
        patientId: PatientId,
        consultationId: ConsultationId,
        vararg diagnoses: Pair<DiagnoseType, String>,
    ) =
        consultationRepository.update(
            UpdateConsultationRequest(
                consultationId = consultationId,
                diagnoses =
                    diagnoses.map { (system, code) -> CreateDiagnosisRequest(code, system) },
                clinicalNote = null,
                finalize = false,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

    private suspend fun storedRanks(consultationId: ConsultationId) = dbQuery {
        ConsultationDiagnosisCodeTable.selectAll()
            .where { ConsultationDiagnosisCodeTable.consultationId eq consultationId.value }
            .orderBy(ConsultationDiagnosisCodeTable.rank)
            .map {
                it[ConsultationDiagnosisCodeTable.rank].toInt() to
                    it[ConsultationDiagnosisCodeTable.diagnosisCode]
            }
    }

    private suspend fun codesFor(consultationId: ConsultationId) =
        consultationRepository
            .findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)!!
            .diagnoses
            .map { it.code }

    @Test
    fun `diagnoses are stored with rank from submitted order and read back in that order`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)

            saveDiagnoses(
                patientId,
                consultationId,
                DiagnoseType.ICPC2 to "T90",
                DiagnoseType.ICPC2 to "K86",
                DiagnoseType.ICD10 to "A00.0",
            )

            assertEquals(listOf(1 to "T90", 2 to "K86", 3 to "A00.0"), storedRanks(consultationId))
            assertEquals(listOf("T90", "K86", "A00.0"), codesFor(consultationId))
            assertEquals(
                listOf("T90", "K86", "A00.0"),
                consultationRepository
                    .listByPatientId(patientId, DEFAULT_ORG_NUMBER)
                    .single()
                    .diagnoses
                    .map { it.code },
            )
        }

    @Test
    fun `saving the same diagnoses in another order moves the main diagnosis`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        saveDiagnoses(
            patientId,
            consultationId,
            DiagnoseType.ICPC2 to "A01",
            DiagnoseType.ICPC2 to "A02",
            DiagnoseType.ICPC2 to "A03",
        )

        saveDiagnoses(
            patientId,
            consultationId,
            DiagnoseType.ICPC2 to "A03",
            DiagnoseType.ICPC2 to "A01",
            DiagnoseType.ICPC2 to "A02",
        )

        assertEquals(listOf(1 to "A03", 2 to "A01", 3 to "A02"), storedRanks(consultationId))
        assertEquals(listOf("A03", "A01", "A02"), codesFor(consultationId))
    }

    @Test
    fun `saving a repeated diagnosis is rejected and keeps the stored diagnoses`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        saveDiagnoses(patientId, consultationId, DiagnoseType.ICPC2 to "A01")

        assertFailsWith<DuplicateDiagnosisException> {
            saveDiagnoses(
                patientId,
                consultationId,
                DiagnoseType.ICPC2 to "A02",
                DiagnoseType.ICPC2 to "A02",
            )
        }

        assertEquals(listOf(1 to "A01"), storedRanks(consultationId))
    }

    @Test
    fun `diagnoses that resolve to the same catalogue code are rejected as repeated`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)

        assertFailsWith<DuplicateDiagnosisException> {
            saveDiagnoses(
                patientId,
                consultationId,
                DiagnoseType.ICD10 to "A000",
                DiagnoseType.ICD10 to "A00.0",
            )
        }

        assertEquals(emptyList(), storedRanks(consultationId))
    }

    @Test
    fun `concurrent saves for one consultation leave one complete ordered set`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val orders =
            listOf(listOf("A01", "A02", "A03"), listOf("A03", "A02", "A01"), listOf("A02", "A01"))

        withContext(Dispatchers.IO) {
            (1..12)
                .map { attempt ->
                    async {
                        val codes = orders[attempt % orders.size]
                        saveDiagnoses(
                            patientId,
                            consultationId,
                            *codes.map { DiagnoseType.ICPC2 to it }.toTypedArray(),
                        )
                    }
                }
                .awaitAll()
        }

        val stored = storedRanks(consultationId)
        assertEquals((1..stored.size).toList(), stored.map { it.first })
        assertEquals(true, stored.map { it.second } in orders)
    }

    @Test
    fun `the same diagnosis can be stored on different consultations`() = runTest {
        val patientId = createPatient()
        val first = createConsultation(patientId)
        val second = createConsultation(patientId)

        saveDiagnoses(patientId, first, DiagnoseType.ICPC2 to "A01")
        saveDiagnoses(patientId, second, DiagnoseType.ICPC2 to "A01")

        assertEquals(listOf(1 to "A01"), storedRanks(first))
        assertEquals(listOf(1 to "A01"), storedRanks(second))
    }

    @Test
    fun `stores the katalog's canonical ICD10 code regardless of dot formatting`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)

        saveDiagnoses(patientId, consultationId, DiagnoseType.ICD10 to "A000")

        assertEquals(listOf(1 to "A00.0"), storedRanks(consultationId))
    }

    @Test
    fun `consultation lookups resolve official catalogue text`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        saveDiagnoses(patientId, consultationId, DiagnoseType.ICPC2 to "A01")

        val expectedText = Diagnose.from(DiagnoseType.ICPC2, "A01")!!.text
        val byPatient =
            consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).flatMap {
                it.diagnoses
            }
        val byConsultation =
            consultationRepository
                .findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)!!
                .diagnoses

        assertEquals(expectedText, byPatient.single().text)
        assertEquals(expectedText, byConsultation.single().text)
    }
}
