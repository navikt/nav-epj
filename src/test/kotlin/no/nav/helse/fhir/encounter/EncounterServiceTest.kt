package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Encounter
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.consultation.Consultation
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.fhir.condition.conditionFhirId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import org.junit.Test

class EncounterServiceTest {

    private val consultationService = mockk<ConsultationService>()
    private val encounterService = EncounterService(consultationService)
    private val hpr = PractitionerHpr("9144889")

    @OptIn(ExperimentalUuidApi::class)
    private fun consultation(
        id: ConsultationId = ConsultationId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
        clinicId: ClinicId = ClinicId(Uuid.generateV4()),
        hpr: List<String> = emptyList(),
        diagnoses: List<Diagnose> = emptyList(),
        status: ConsultationStatus = ConsultationStatus.IN_PROGRESS,
        startedAt: LocalDateTime = LocalDateTime.now().minusHours(1),
        endedAt: LocalDateTime? = null,
    ) =
        Consultation(
            id = id,
            patientId = patientId,
            clinicId = clinicId,
            hpr = hpr,
            clinicalNotes = emptyList(),
            diagnoses = diagnoses,
            startedAt = startedAt,
            endedAt = endedAt,
            status = status,
            reasonForVisit = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById maps subject, participants, period and status`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        val start = LocalDateTime.of(2026, 9, 1, 9, 30, 15, 120_000_000)
        val end = LocalDateTime.of(2026, 9, 1, 10, 5, 40)
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(
                id = consultationId,
                patientId = patientId,
                hpr = listOf("1234567"),
                status = ConsultationStatus.FINISHED,
                startedAt = start,
                endedAt = end,
            )

        val encounter =
            encounterService.getEncounterById(EncounterId(consultationId.value), DEFAULT_ORG_NUMBER)

        assertEquals("Patient/${patientId.value}", encounter.subject?.reference?.value)
        assertEquals(
            "Practitioner/1234567",
            encounter.participant.single().individual?.reference?.value,
        )
        assertEquals(Encounter.EncounterStatus.Finished, encounter.status.value)
        assertEquals("2026-09-01T09:30:15.12Z", encounter.period?.start?.value.toString())
        assertEquals("2026-09-01T10:05:40Z", encounter.period?.end?.value.toString())
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById always emits a serviceProvider organization reference required by downstream FHIR consumers such as syk-inn and smart-on-fhir-validator`() =
        runTest {
            val consultationId = ConsultationId(Uuid.generateV4())
            coEvery {
                consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER)
            } returns consultation(id = consultationId)

            val encounter =
                encounterService.getEncounterById(
                    EncounterId(consultationId.value),
                    DEFAULT_ORG_NUMBER,
                )

            assertNotNull(encounter.serviceProvider)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById maps serviceProvider to the consultation's organization`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val clinicId = ClinicId(Uuid.generateV4())
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(id = consultationId, clinicId = clinicId)

        val encounter =
            encounterService.getEncounterById(EncounterId(consultationId.value), DEFAULT_ORG_NUMBER)

        assertEquals("Organization/${clinicId.value}", encounter.serviceProvider?.reference?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById maps ICPC-2 and ICD-10 reasonCode to the correct official system`() =
        runTest {
            val consultationId = ConsultationId(Uuid.generateV4())
            val diagnoses =
                listOf(
                    Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                    Diagnose(system = DiagnoseType.ICD10, code = "A00", text = "Diagnose A00"),
                )
            coEvery {
                consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER)
            } returns consultation(id = consultationId, diagnoses = diagnoses)

            val encounter =
                encounterService.getEncounterById(
                    EncounterId(consultationId.value),
                    DEFAULT_ORG_NUMBER,
                )

            val systems = encounter.reasonCode.map { it.coding.single().system?.value }
            assertEquals(listOf("urn:oid:${ICPC2.OID}", "urn:oid:${ICD10.OID}"), systems)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById ranks diagnoses from one in the stored order`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val diagnoses =
            listOf(
                Diagnose(system = DiagnoseType.ICD10, code = "Z99", text = "Diagnose Z99"),
                Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                Diagnose(system = DiagnoseType.ICPC2, code = "B02", text = "Diagnose B02"),
            )
        coEvery { consultationService.getConsultation(consultationId, DEFAULT_ORG_NUMBER) } returns
            consultation(id = consultationId, diagnoses = diagnoses)

        val encounter =
            encounterService.getEncounterById(EncounterId(consultationId.value), DEFAULT_ORG_NUMBER)

        assertEquals(listOf(1, 2, 3), encounter.diagnosis.map { it.rank?.value })
        assertEquals(
            diagnoses.map { conditionFhirId(consultationId, it) },
            encounter.diagnosis.map { it.condition.reference?.value?.removePrefix("Condition/") },
        )
        assertEquals(
            listOf("Z99", "A01", "B02"),
            encounter.reasonCode.map { it.coding.single().code?.value },
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncountersByPatient returns a searchset bundle with one entry per consultation`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                listOf(consultation(patientId = patientId), consultation(patientId = patientId))

            val bundle =
                encounterService.getEncountersByPatient(
                    PatientInputId(patientId.value),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(Bundle.BundleType.Searchset, bundle.type.value)
            assertEquals(2, bundle.entry.size)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncountersByPatient returns a valid empty searchset bundle when the patient has no consultations`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
                emptyList()

            val bundle =
                encounterService.getEncountersByPatient(
                    PatientInputId(patientId.value),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(Bundle.BundleType.Searchset, bundle.type.value)
            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getActiveEncounterByPatient returns null when there is no active consultation`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery {
                consultationService.getActiveConsultation(patientId, hpr, DEFAULT_ORG_NUMBER)
            } returns null

            val encounter =
                encounterService.getActiveEncounterByPatient(
                    PatientInputId(patientId.value),
                    hpr,
                    DEFAULT_ORG_NUMBER,
                )

            assertNull(encounter)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getActiveEncounterByPatient maps the active consultation`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        coEvery {
            consultationService.getActiveConsultation(patientId, hpr, DEFAULT_ORG_NUMBER)
        } returns consultation(id = consultationId, patientId = patientId, hpr = listOf(hpr.value))

        val encounter =
            encounterService.getActiveEncounterByPatient(
                PatientInputId(patientId.value),
                hpr,
                DEFAULT_ORG_NUMBER,
            )

        assertNotNull(encounter)
        assertEquals(consultationId.value.toString(), encounter.id)
        assertEquals("Patient/${patientId.value}", encounter.subject?.reference?.value)
    }
}
