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
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import org.junit.Test

class EncounterServiceTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val encounterService = EncounterService(konsultasjonService)

    @OptIn(ExperimentalUuidApi::class)
    private fun konsultasjon(
        id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId: PasientId = PasientId(Uuid.generateV4()),
        hpr: List<String> = emptyList(),
        diagnoser: List<Diagnose> = emptyList(),
        status: KonsultasjonStatus = KonsultasjonStatus.PÅGÅENDE,
        startetTidspunkt: LocalDateTime = LocalDateTime.now().minusHours(1),
        avsluttetTidspunkt: LocalDateTime? = null,
    ) =
        Konsultasjon(
            id = id,
            pasientId = pasientId,
            hpr = hpr,
            journalnotat = emptyList(),
            diagnoser = diagnoser,
            startetTidspunkt = startetTidspunkt,
            avsluttetTidspunkt = avsluttetTidspunkt,
            status = status,
            problemstilling = null,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById maps subject, participants, period and status`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val start = LocalDateTime.now().minusHours(2)
        val end = LocalDateTime.now().minusHours(1)
        coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
            konsultasjon(
                id = konsultasjonId,
                pasientId = pasientId,
                hpr = listOf("1234567"),
                status = KonsultasjonStatus.FULLFØRT,
                startetTidspunkt = start,
                avsluttetTidspunkt = end,
            )

        val encounter = encounterService.getEncounterById(EncounterId(konsultasjonId.value))

        assertEquals("Patient/${pasientId.value}", encounter.subject?.reference?.value)
        assertEquals(
            "Practitioner/1234567",
            encounter.participant.single().individual?.reference?.value,
        )
        assertEquals(Encounter.EncounterStatus.Finished, encounter.status.value)
        assertEquals(
            start.toString(),
            encounter.period?.start?.value.toString().substringBefore("Z"),
        )
        assertEquals(end.toString(), encounter.period?.end?.value.toString().substringBefore("Z"))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById omits serviceProvider when no organization is linked to the konsultasjon`() =
        runTest {
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
                konsultasjon(id = konsultasjonId)

            val encounter = encounterService.getEncounterById(EncounterId(konsultasjonId.value))

            assertNull(encounter.serviceProvider)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncounterById maps ICPC-2 and ICD-10 reasonCode to the correct official system`() =
        runTest {
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            val diagnoser =
                listOf(
                    Diagnose(system = DiagnoseType.ICPC2, code = "A01", text = "Diagnose A01"),
                    Diagnose(system = DiagnoseType.ICD10, code = "A00", text = "Diagnose A00"),
                )
            coEvery { konsultasjonService.getKonsultasjon(konsultasjonId) } returns
                konsultasjon(id = konsultasjonId, diagnoser = diagnoser)

            val encounter = encounterService.getEncounterById(EncounterId(konsultasjonId.value))

            val systems = encounter.reasonCode.map { it.coding.single().system?.value }
            assertEquals(listOf("urn:oid:${ICPC2.OID}", "urn:oid:${ICD10.OID}"), systems)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncountersByPatient returns a searchset bundle with one entry per konsultasjon`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { konsultasjonService.getKonsultasjoner(pasientId) } returns
                listOf(konsultasjon(pasientId = pasientId), konsultasjon(pasientId = pasientId))

            val bundle = encounterService.getEncountersByPatient(PatientInputId(pasientId.value))

            assertEquals(Bundle.BundleType.Searchset, bundle.type.value)
            assertEquals(2, bundle.entry.size)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getEncountersByPatient returns a valid empty searchset bundle when the patient has no konsultasjoner`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { konsultasjonService.getKonsultasjoner(pasientId) } returns emptyList()

            val bundle = encounterService.getEncountersByPatient(PatientInputId(pasientId.value))

            assertEquals(Bundle.BundleType.Searchset, bundle.type.value)
            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getActiveEncounterByPatient returns null when there is no active konsultasjon`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { konsultasjonService.getAktivKonsultasjon(pasientId) } returns null

            val encounter =
                encounterService.getActiveEncounterByPatient(PatientInputId(pasientId.value))

            assertNull(encounter)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getActiveEncounterByPatient maps the active konsultasjon`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        coEvery { konsultasjonService.getAktivKonsultasjon(pasientId) } returns
            konsultasjon(id = konsultasjonId, pasientId = pasientId)

        val encounter =
            encounterService.getActiveEncounterByPatient(PatientInputId(pasientId.value))

        assertNotNull(encounter)
        assertEquals("Patient/${pasientId.value}", encounter.subject?.reference?.value)
    }
}
