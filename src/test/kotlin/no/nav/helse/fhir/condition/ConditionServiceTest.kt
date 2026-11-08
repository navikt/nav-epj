package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Condition
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.consultation.Consultation
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import org.junit.Test

class ConditionServiceTest {

    private val consultationService = mockk<ConsultationService>()
    private val conditionService = ConditionService(consultationService)

    @OptIn(ExperimentalUuidApi::class)
    private fun consultation(
        id: ConsultationId = ConsultationId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
        diagnoses: List<Diagnose> = emptyList(),
    ) =
        Consultation(
            id = id,
            patientId = patientId,
            clinicId = ClinicId(Uuid.generateV4()),
            hpr = emptyList(),
            clinicalNotes = emptyList(),
            diagnoses = diagnoses,
            startedAt = LocalDateTime.now().minusHours(1),
            endedAt = null,
            status = ConsultationStatus.IN_PROGRESS,
            reasonForVisit = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByPatientId returns a Condition per diagnosis with subject and encounter references`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val consultationId = ConsultationId(Uuid.generateV4())
            val diagnoses =
                listOf(
                    Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                    Diagnose(system = DiagnoseType.ICD10, code = "A00", text = "Diagnose A00"),
                )
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                listOf(
                    consultation(id = consultationId, patientId = patientId, diagnoses = diagnoses)
                )

            val bundle =
                conditionService.getConditionsByPatientId(
                    PatientInputId(patientId.value),
                    DEFAULT_ORG_NUMBER,
                )

            val conditions = bundle.entry.map { it.resource as Condition }
            assertEquals(2, conditions.size)
            conditions.forEach { condition ->
                assertEquals("Patient/${patientId.value}", condition.subject.reference?.value)
                assertEquals(
                    "Encounter/${consultationId.value}",
                    condition.encounter?.reference?.value,
                )
            }
            assertEquals(
                "urn:oid:${ICPC2.OID}",
                conditions[0].code?.coding?.single()?.system?.value,
            )
            assertEquals(
                "urn:oid:${ICD10.OID}",
                conditions[1].code?.coding?.single()?.system?.value,
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Conditions keep the consultation diagnosis order and carry the encounter-diagnosis category`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val consultationId = ConsultationId(Uuid.generateV4())
            val diagnoses =
                listOf(
                    Diagnose(system = DiagnoseType.ICD10, code = "Z99", text = "Diagnose Z99"),
                    Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                    Diagnose(system = DiagnoseType.ICD10, code = "A00", text = "Diagnose A00"),
                )
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                listOf(
                    consultation(id = consultationId, patientId = patientId, diagnoses = diagnoses)
                )

            val conditions =
                conditionService
                    .getConditionsByPatientId(PatientInputId(patientId.value), DEFAULT_ORG_NUMBER)
                    .entry
                    .map { it.resource as Condition }

            assertEquals(
                listOf("Z99", "A01", "A00"),
                conditions.map { it.code?.coding?.single()?.code?.value },
            )
            conditions.forEach { condition ->
                val category = condition.category.single().coding.single()
                assertEquals(
                    "http://terminology.hl7.org/CodeSystem/condition-category",
                    category.system?.value,
                )
                assertEquals("encounter-diagnosis", category.code?.value)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByPatientId returns an empty bundle when the patient has no consultations`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                emptyList()

            val bundle =
                conditionService.getConditionsByPatientId(
                    PatientInputId(patientId.value),
                    DEFAULT_ORG_NUMBER,
                )

            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByEncounterId returns a Condition with the encounter reference`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        val diagnosis = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01")
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(id = consultationId, patientId = patientId, diagnoses = listOf(diagnosis))

        val bundle =
            conditionService.getConditionsByEncounterId(
                EncounterId(consultationId.value),
                DEFAULT_ORG_NUMBER,
            )

        val condition = (bundle.entry.single().resource as Condition)
        assertEquals("Patient/${patientId.value}", condition.subject.reference?.value)
        assertEquals("Encounter/${consultationId.value}", condition.encounter?.reference?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByEncounterId propagates ConsultationNotFoundException for an unknown encounter`() =
        runTest {
            val consultationId = ConsultationId(Uuid.generateV4())
            coEvery {
                consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER)
            } throws ConsultationNotFoundException(consultationId)

            try {
                conditionService.getConditionsByEncounterId(
                    EncounterId(consultationId.value),
                    DEFAULT_ORG_NUMBER,
                )
                error("Expected ConsultationNotFoundException")
            } catch (e: ConsultationNotFoundException) {
                assertNotNull(e)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Condition id is deterministic across repeated lookups of the same diagnosis`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        val diagnosis = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01")
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(id = consultationId, patientId = patientId, diagnoses = listOf(diagnosis))

        val firstId =
            (conditionService
                    .getConditionsByEncounterId(
                        EncounterId(consultationId.value),
                        DEFAULT_ORG_NUMBER,
                    )
                    .entry
                    .single()
                    .resource as Condition)
                .id
        val secondId =
            (conditionService
                    .getConditionsByEncounterId(
                        EncounterId(consultationId.value),
                        DEFAULT_ORG_NUMBER,
                    )
                    .entry
                    .single()
                    .resource as Condition)
                .id

        assertEquals(firstId, secondId)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Condition id differs between diagnoses on the same consultation`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        val diagnoses =
            listOf(
                Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                Diagnose(system = DiagnoseType.ICPC2, code = "A02", text = "Diagnose A02"),
            )
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(id = consultationId, patientId = patientId, diagnoses = diagnoses)

        val conditions =
            conditionService
                .getConditionsByEncounterId(EncounterId(consultationId.value), DEFAULT_ORG_NUMBER)
                .entry
                .map { it.resource as Condition }

        assertNotEquals(conditions[0].id, conditions[1].id)
    }
}
