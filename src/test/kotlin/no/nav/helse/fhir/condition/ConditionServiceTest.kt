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
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.Test

class ConditionServiceTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val conditionService = ConditionService(konsultasjonService)

    @OptIn(ExperimentalUuidApi::class)
    private fun konsultasjon(
        id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId: PasientId = PasientId(Uuid.generateV4()),
        diagnoser: List<Diagnose> = emptyList(),
    ) =
        Konsultasjon(
            id = id,
            pasientId = pasientId,
            hpr = emptyList(),
            journalnotat = emptyList(),
            diagnoser = diagnoser,
            startetTidspunkt = LocalDateTime.now().minusHours(1),
            avsluttetTidspunkt = null,
            status = KonsultasjonStatus.PÅGÅENDE,
            problemstilling = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByPatientId returns a Condition per diagnose with subject and encounter references`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            val diagnoser =
                listOf(
                    Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                    Diagnose(system = DiagnoseType.ICD10, code = "A00", text = "Diagnose A00"),
                )
            coEvery { konsultasjonService.getKonsultasjoner(pasientId) } returns
                listOf(
                    konsultasjon(id = konsultasjonId, pasientId = pasientId, diagnoser = diagnoser)
                )

            val bundle = conditionService.getConditionsByPatientId(PatientInputId(pasientId.value))

            val conditions = bundle.entry.map { it.resource as Condition }
            assertEquals(2, conditions.size)
            conditions.forEach { condition ->
                assertEquals("Patient/${pasientId.value}", condition.subject.reference?.value)
                assertEquals(
                    "Encounter/${konsultasjonId.value}",
                    condition.encounter?.reference?.value,
                )
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByPatientId returns an empty bundle when the patient has no konsultasjoner`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { konsultasjonService.getKonsultasjoner(pasientId) } returns emptyList()

            val bundle = conditionService.getConditionsByPatientId(PatientInputId(pasientId.value))

            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByEncounterId returns a Condition with the encounter reference`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val diagnose = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01")
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
            konsultasjon(id = konsultasjonId, pasientId = pasientId, diagnoser = listOf(diagnose))

        val bundle = conditionService.getConditionsByEncounterId(EncounterId(konsultasjonId.value))

        val condition = (bundle.entry.single().resource as Condition)
        assertEquals("Patient/${pasientId.value}", condition.subject.reference?.value)
        assertEquals("Encounter/${konsultasjonId.value}", condition.encounter?.reference?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getConditionsByEncounterId propagates KonsultasjonNotFoundException for an unknown encounter`() =
        runTest {
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } throws
                KonsultasjonNotFoundException(konsultasjonId)

            try {
                conditionService.getConditionsByEncounterId(EncounterId(konsultasjonId.value))
                error("Expected KonsultasjonNotFoundException")
            } catch (e: KonsultasjonNotFoundException) {
                assertNotNull(e)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Condition id is deterministic across repeated lookups of the same diagnose`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val diagnose = Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01")
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
            konsultasjon(id = konsultasjonId, pasientId = pasientId, diagnoser = listOf(diagnose))

        val førsteId =
            (conditionService
                    .getConditionsByEncounterId(EncounterId(konsultasjonId.value))
                    .entry
                    .single()
                    .resource as Condition)
                .id
        val andreId =
            (conditionService
                    .getConditionsByEncounterId(EncounterId(konsultasjonId.value))
                    .entry
                    .single()
                    .resource as Condition)
                .id

        assertEquals(førsteId, andreId)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Condition id differs between diagnoses on the same konsultasjon`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val diagnoser =
            listOf(
                Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                Diagnose(system = DiagnoseType.ICPC2, code = "A02", text = "Diagnose A02"),
            )
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
            konsultasjon(id = konsultasjonId, pasientId = pasientId, diagnoser = diagnoser)

        val conditions =
            conditionService
                .getConditionsByEncounterId(EncounterId(konsultasjonId.value))
                .entry
                .map { it.resource as Condition }

        assertNotEquals(conditions[0].id, conditions[1].id)
    }
}
