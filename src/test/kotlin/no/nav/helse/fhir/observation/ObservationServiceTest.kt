package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Observation
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toKotlinLocalDateTime
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.maaling.Maaling
import no.nav.helse.epj.maaling.MaalingId
import no.nav.helse.epj.maaling.MaalingService
import no.nav.helse.epj.maaling.MaalingStatus
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import org.junit.Test

class ObservationServiceTest {

    private val maalingService = mockk<MaalingService>()
    private val observationService = ObservationService(maalingService)

    @OptIn(ExperimentalUuidApi::class)
    private fun maaling(
        id: MaalingId = MaalingId(Uuid.generateV4()),
        pasientId: PasientId = PasientId(Uuid.generateV4()),
        konsultasjonId: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        hpr: HelsepersonellHpr? = HelsepersonellHpr("123"),
        loincKode: String = "8310-5",
        loincVisningsnavn: String = "Body temperature",
        verdi: BigDecimal = BigDecimal("37.2000"),
        enhetKode: String = "Cel",
        enhetVisningsnavn: String = "degree Celsius",
        effektivTidspunkt: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30),
        status: MaalingStatus = MaalingStatus.FINAL,
    ) =
        Maaling(
            id = id,
            pasientId = pasientId,
            konsultasjonId = konsultasjonId,
            hpr = hpr,
            loincKode = loincKode,
            loincVisningsnavn = loincVisningsnavn,
            verdi = verdi,
            enhetKode = enhetKode,
            enhetVisningsnavn = enhetVisningsnavn,
            effektivTidspunkt = effektivTidspunkt,
            status = status,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById returns null when the maaling does not exist`() = runTest {
        val id = ObservationId(Uuid.generateV4())
        coEvery { maalingService.getMaaling(MaalingId(id.value)) } returns null

        assertNull(observationService.getObservationById(id))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById maps subject, encounter, LOINC code and UCUM quantity`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val target = maaling(pasientId = pasientId, konsultasjonId = konsultasjonId)
        coEvery { maalingService.getMaaling(target.id) } returns target

        val observation = observationService.getObservationById(ObservationId(target.id.value))

        requireNotNull(observation)
        assertEquals(target.id.value.toString(), observation.id)
        assertEquals("Patient/${pasientId.value}", observation.subject?.reference?.value)
        assertEquals("Encounter/${konsultasjonId.value}", observation.encounter?.reference?.value)

        val coding = observation.code.coding.single()
        assertEquals("http://loinc.org", coding.system?.value)
        assertEquals("8310-5", coding.code?.value)
        assertEquals("Body temperature", coding.display?.value)

        val quantity = (observation.value as Observation.Value.Quantity).value
        assertEquals("http://unitsofmeasure.org", quantity.system?.value)
        assertEquals("Cel", quantity.code?.value)
        assertEquals("degree Celsius", quantity.unit?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById maps performer when hpr is present`() = runTest {
        val target = maaling(hpr = HelsepersonellHpr("999"))
        coEvery { maalingService.getMaaling(target.id) } returns target

        val observation = observationService.getObservationById(ObservationId(target.id.value))

        requireNotNull(observation)
        assertEquals("Practitioner/999", observation.performer.single().reference?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById omits performer when hpr is absent`() = runTest {
        val target = maaling(hpr = null)
        coEvery { maalingService.getMaaling(target.id) } returns target

        val observation = observationService.getObservationById(ObservationId(target.id.value))

        requireNotNull(observation)
        assertTrue(observation.performer.isEmpty())
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById maps effektivTidspunkt as the effective dateTime`() = runTest {
        val tidspunkt = LocalDateTime.of(2025, 3, 4, 9, 15)
        val target = maaling(effektivTidspunkt = tidspunkt)
        coEvery { maalingService.getMaaling(target.id) } returns target

        val observation = observationService.getObservationById(ObservationId(target.id.value))

        requireNotNull(observation)
        val effective = observation.effective as Observation.Effective.DateTime
        assertEquals(
            FhirDateTime.DateTime(tidspunkt.toKotlinLocalDateTime(), UtcOffset.ZERO),
            effective.value.value,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById preserves decimal precision of the measured value`() = runTest {
        val target = maaling(verdi = BigDecimal("5.6789"))
        coEvery { maalingService.getMaaling(target.id) } returns target

        val observation = observationService.getObservationById(ObservationId(target.id.value))

        requireNotNull(observation)
        val quantity = (observation.value as Observation.Value.Quantity).value
        val verdi = requireNotNull(quantity.value?.value) { "Quantity mangler value" }
        assertEquals(0, BigDecimal(verdi.toString()).compareTo(BigDecimal("5.6789")))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `every MaalingStatus maps to the corresponding FHIR Observation status`() = runTest {
        val expected =
            mapOf(
                MaalingStatus.REGISTERED to "registered",
                MaalingStatus.PRELIMINARY to "preliminary",
                MaalingStatus.FINAL to "final",
                MaalingStatus.AMENDED to "amended",
                MaalingStatus.CORRECTED to "corrected",
                MaalingStatus.CANCELLED to "cancelled",
                MaalingStatus.ENTERED_IN_ERROR to "entered-in-error",
                MaalingStatus.UNKNOWN to "unknown",
            )
        assertEquals(MaalingStatus.entries.toSet(), expected.keys)

        expected.forEach { (status, expectedCode) ->
            val target = maaling(status = status)
            coEvery { maalingService.getMaaling(target.id) } returns target

            val observation = observationService.getObservationById(ObservationId(target.id.value))

            requireNotNull(observation)
            assertEquals(expectedCode, observation.status.value?.getCode())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations returns an empty searchset bundle when the patient has no maalinger`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { maalingService.getMaalingerForPasient(pasientId) } returns emptyList()

            val bundle =
                observationService.searchObservations(
                    patientId = PatientInputId(pasientId.value),
                    encounterId = null,
                    code = null,
                )

            assertEquals(com.google.fhir.model.r4.Bundle.BundleType.Searchset, bundle.type.value)
            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations returns every maaling for the patient without filters`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val forste = maaling(pasientId = pasientId)
        val andre = maaling(pasientId = pasientId)
        coEvery { maalingService.getMaalingerForPasient(pasientId) } returns listOf(forste, andre)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(pasientId.value),
                encounterId = null,
                code = null,
            )

        assertEquals(
            setOf(forste.id.value.toString(), andre.id.value.toString()),
            bundle.entry.map { (it.resource as Observation).id }.toSet(),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations filters by encounter`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonA = KonsultasjonId(Uuid.generateV4())
        val konsultasjonB = KonsultasjonId(Uuid.generateV4())
        val maalingA = maaling(pasientId = pasientId, konsultasjonId = konsultasjonA)
        val maalingB = maaling(pasientId = pasientId, konsultasjonId = konsultasjonB)
        coEvery { maalingService.getMaalingerForPasient(pasientId) } returns
            listOf(maalingA, maalingB)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(pasientId.value),
                encounterId = EncounterId(konsultasjonA.value),
                code = null,
            )

        assertEquals(
            maalingA.id.value.toString(),
            (bundle.entry.single().resource as Observation).id,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations filters by LOINC code`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val temperatur = maaling(pasientId = pasientId, loincKode = "8310-5")
        val glukose = maaling(pasientId = pasientId, loincKode = "2339-0")
        coEvery { maalingService.getMaalingerForPasient(pasientId) } returns
            listOf(temperatur, glukose)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(pasientId.value),
                encounterId = null,
                code = "2339-0",
            )

        assertEquals(
            glukose.id.value.toString(),
            (bundle.entry.single().resource as Observation).id,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations combines encounter and code filters`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjonA = KonsultasjonId(Uuid.generateV4())
        val konsultasjonB = KonsultasjonId(Uuid.generateV4())
        val match =
            maaling(pasientId = pasientId, konsultasjonId = konsultasjonA, loincKode = "8310-5")
        val wrongEncounter =
            maaling(pasientId = pasientId, konsultasjonId = konsultasjonB, loincKode = "8310-5")
        val wrongCode =
            maaling(pasientId = pasientId, konsultasjonId = konsultasjonA, loincKode = "2339-0")
        coEvery { maalingService.getMaalingerForPasient(pasientId) } returns
            listOf(match, wrongEncounter, wrongCode)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(pasientId.value),
                encounterId = EncounterId(konsultasjonA.value),
                code = "8310-5",
            )

        assertEquals(match.id.value.toString(), (bundle.entry.single().resource as Observation).id)
    }
}
