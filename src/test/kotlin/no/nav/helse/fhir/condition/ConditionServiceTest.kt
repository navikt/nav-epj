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
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import org.junit.Test

class ConditionServiceTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val conditionService = ConditionService(konsultasjonService)

    @OptIn(ExperimentalUuidApi::class)
    private fun konsultasjon(
        id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
        diagnoser: List<Diagnose> = emptyList(),
    ) =
        Konsultasjon(
            id = id,
            patientId = patientId,
            clinicId = ClinicId(Uuid.generateV4()),
            hpr = emptyList(),
            journalnotat = emptyList(),
            diagnoser = diagnoser,
            startetTidspunkt = LocalDateTime.now().minusHours(1),
            avsluttetTidspunkt = null,
            status = KonsultasjonStatus.PAAGAAENDE,
            problemstilling = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByPatientId returns a Condition per diagnose with subject and encounter references`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            val diagnoser =
                listOf(
                    Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                    Diagnose(system = DiagnoseType.ICD10, code = "A00", text = "Diagnose A00"),
                )
            coEvery { konsultasjonService.getKonsultasjoner(patientId, DEFAULT_ORG_NUMBER) } returns
                listOf(
                    konsultasjon(id = konsultasjonId, patientId = patientId, diagnoser = diagnoser)
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
                    "Encounter/${konsultasjonId.value}",
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
    fun `getConditionsByPatientId returns an empty bundle when the patient has no konsultasjoner`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery { konsultasjonService.getKonsultasjoner(patientId, DEFAULT_ORG_NUMBER) } returns
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
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val diagnose = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01")
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId, DEFAULT_ORG_NUMBER) } returns
            konsultasjon(id = konsultasjonId, patientId = patientId, diagnoser = listOf(diagnose))

        val bundle =
            conditionService.getConditionsByEncounterId(
                EncounterId(konsultasjonId.value),
                DEFAULT_ORG_NUMBER,
            )

        val condition = (bundle.entry.single().resource as Condition)
        assertEquals("Patient/${patientId.value}", condition.subject.reference?.value)
        assertEquals("Encounter/${konsultasjonId.value}", condition.encounter?.reference?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByEncounterId propagates KonsultasjonNotFoundException for an unknown encounter`() =
        runTest {
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            coEvery {
                konsultasjonService.getKonsultasjon(konsultasjonId, DEFAULT_ORG_NUMBER)
            } throws KonsultasjonNotFoundException(konsultasjonId)

            try {
                conditionService.getConditionsByEncounterId(
                    EncounterId(konsultasjonId.value),
                    DEFAULT_ORG_NUMBER,
                )
                error("Expected KonsultasjonNotFoundException")
            } catch (e: KonsultasjonNotFoundException) {
                assertNotNull(e)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Condition id is deterministic across repeated lookups of the same diagnose`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val diagnose = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01")
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId, DEFAULT_ORG_NUMBER) } returns
            konsultasjon(id = konsultasjonId, patientId = patientId, diagnoser = listOf(diagnose))

        val firstId =
            (conditionService
                    .getConditionsByEncounterId(
                        EncounterId(konsultasjonId.value),
                        DEFAULT_ORG_NUMBER,
                    )
                    .entry
                    .single()
                    .resource as Condition)
                .id
        val secondId =
            (conditionService
                    .getConditionsByEncounterId(
                        EncounterId(konsultasjonId.value),
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
    fun `Condition id differs between diagnoses on the same konsultasjon`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val diagnoser =
            listOf(
                Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                Diagnose(system = DiagnoseType.ICPC2, code = "A02", text = "Diagnose A02"),
            )
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId, DEFAULT_ORG_NUMBER) } returns
            konsultasjon(id = konsultasjonId, patientId = patientId, diagnoser = diagnoser)

        val conditions =
            conditionService
                .getConditionsByEncounterId(EncounterId(konsultasjonId.value), DEFAULT_ORG_NUMBER)
                .entry
                .map { it.resource as Condition }

        assertNotEquals(conditions[0].id, conditions[1].id)
    }
}
