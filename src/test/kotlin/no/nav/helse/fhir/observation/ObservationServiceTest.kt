package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Observation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toKotlinLocalDateTime
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.measurement.CreateMeasurementRequest
import no.nav.helse.epj.measurement.Measurement
import no.nav.helse.epj.measurement.MeasurementId
import no.nav.helse.epj.measurement.MeasurementService
import no.nav.helse.epj.measurement.MeasurementStatus
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class ObservationServiceTest {

    private val measurementService = mockk<MeasurementService>()
    private val observationService = ObservationService(measurementService)

    @OptIn(ExperimentalUuidApi::class)
    private fun measurement(
        id: MeasurementId = MeasurementId(Uuid.generateV4()),
        patientId: PatientId = PatientId(Uuid.generateV4()),
        consultationId: ConsultationId = ConsultationId(Uuid.generateV4()),
        hpr: PractitionerHpr? = PractitionerHpr("123"),
        loincCode: String = "8310-5",
        loincDisplay: String = "Body temperature",
        value: BigDecimal = BigDecimal("37.2000"),
        unitCode: String = "Cel",
        unitDisplay: String = "degree Celsius",
        effectiveAt: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30),
        status: MeasurementStatus = MeasurementStatus.FINAL,
    ) =
        Measurement(
            id = id,
            patientId = patientId,
            consultationId = consultationId,
            hpr = hpr,
            loincCode = loincCode,
            loincDisplay = loincDisplay,
            value = value,
            unitCode = unitCode,
            unitDisplay = unitDisplay,
            effectiveAt = effectiveAt,
            status = status,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createObservation persists through MeasurementService and maps the server-generated id back`() =
        runTest {
            val request =
                CreateMeasurementRequest(
                    patientId = PatientId(Uuid.generateV4()),
                    consultationId = ConsultationId(Uuid.generateV4()),
                    hpr = null,
                    loincCode = "8310-5",
                    loincDisplay = "Body temperature",
                    value = BigDecimal("37.2000"),
                    unitCode = "Cel",
                    unitDisplay = "degree Celsius",
                    effectiveAt = LocalDateTime.of(2025, 1, 15, 10, 30),
                    status = MeasurementStatus.FINAL,
                )
            val persisted =
                measurement(
                    patientId = request.patientId,
                    consultationId = request.consultationId,
                    hpr = request.hpr,
                    loincCode = request.loincCode,
                    loincDisplay = request.loincDisplay,
                    value = request.value,
                    unitCode = request.unitCode,
                    unitDisplay = request.unitDisplay,
                    effectiveAt = request.effectiveAt,
                    status = request.status,
                )
            coEvery { measurementService.createMeasurement(request, DEFAULT_ORG_NUMBER) } returns
                persisted

            val observation = observationService.createObservation(request, DEFAULT_ORG_NUMBER)

            assertEquals(persisted.id.value.toString(), observation.id)
            coVerify(exactly = 1) {
                measurementService.createMeasurement(request, DEFAULT_ORG_NUMBER)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createObservation propagates encounter-not-found without swallowing the error`() =
        runTest {
            val request =
                CreateMeasurementRequest(
                    patientId = PatientId(Uuid.generateV4()),
                    consultationId = ConsultationId(Uuid.generateV4()),
                    hpr = null,
                    loincCode = "8310-5",
                    loincDisplay = "Body temperature",
                    value = BigDecimal("37.2000"),
                    unitCode = "Cel",
                    unitDisplay = "degree Celsius",
                    effectiveAt = LocalDateTime.of(2025, 1, 15, 10, 30),
                    status = MeasurementStatus.FINAL,
                )
            coEvery { measurementService.createMeasurement(request, DEFAULT_ORG_NUMBER) } throws
                ConsultationNotFoundException(request.consultationId)

            assertFailsWith<ConsultationNotFoundException> {
                observationService.createObservation(request, DEFAULT_ORG_NUMBER)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById returns null when the measurement does not exist`() = runTest {
        val id = ObservationId(Uuid.generateV4())
        coEvery {
            measurementService.getMeasurement(MeasurementId(id.value), DEFAULT_ORG_NUMBER)
        } returns null

        assertNull(observationService.getObservationById(id, DEFAULT_ORG_NUMBER))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById maps subject, encounter, LOINC code and UCUM quantity`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationId = ConsultationId(Uuid.generateV4())
        val target = measurement(patientId = patientId, consultationId = consultationId)
        coEvery { measurementService.getMeasurement(target.id, DEFAULT_ORG_NUMBER) } returns target

        val observation =
            observationService.getObservationById(
                ObservationId(target.id.value),
                DEFAULT_ORG_NUMBER,
            )

        requireNotNull(observation)
        assertEquals(target.id.value.toString(), observation.id)
        assertEquals("Patient/${patientId.value}", observation.subject?.reference?.value)
        assertEquals("Encounter/${consultationId.value}", observation.encounter?.reference?.value)

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
        val target = measurement(hpr = PractitionerHpr("999"))
        coEvery { measurementService.getMeasurement(target.id, DEFAULT_ORG_NUMBER) } returns target

        val observation =
            observationService.getObservationById(
                ObservationId(target.id.value),
                DEFAULT_ORG_NUMBER,
            )

        requireNotNull(observation)
        assertEquals("Practitioner/999", observation.performer.single().reference?.value)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById omits performer when hpr is absent`() = runTest {
        val target = measurement(hpr = null)
        coEvery { measurementService.getMeasurement(target.id, DEFAULT_ORG_NUMBER) } returns target

        val observation =
            observationService.getObservationById(
                ObservationId(target.id.value),
                DEFAULT_ORG_NUMBER,
            )

        requireNotNull(observation)
        assertTrue(observation.performer.isEmpty())
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById maps effectiveAt as the effective dateTime`() = runTest {
        val effectiveAt = LocalDateTime.of(2025, 3, 4, 9, 15)
        val target = measurement(effectiveAt = effectiveAt)
        coEvery { measurementService.getMeasurement(target.id, DEFAULT_ORG_NUMBER) } returns target

        val observation =
            observationService.getObservationById(
                ObservationId(target.id.value),
                DEFAULT_ORG_NUMBER,
            )

        requireNotNull(observation)
        val effective = observation.effective as Observation.Effective.DateTime
        assertEquals(
            FhirDateTime.DateTime(effectiveAt.toKotlinLocalDateTime(), UtcOffset.ZERO),
            effective.value.value,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getObservationById preserves decimal precision of the measured value`() = runTest {
        val target = measurement(value = BigDecimal("5.6789"))
        coEvery { measurementService.getMeasurement(target.id, DEFAULT_ORG_NUMBER) } returns target

        val observation =
            observationService.getObservationById(
                ObservationId(target.id.value),
                DEFAULT_ORG_NUMBER,
            )

        requireNotNull(observation)
        val quantity = (observation.value as Observation.Value.Quantity).value
        val value = requireNotNull(quantity.value?.value) { "Quantity mangler value" }
        assertEquals(0, BigDecimal(value.toString()).compareTo(BigDecimal("5.6789")))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `every MeasurementStatus maps to the corresponding FHIR Observation status`() = runTest {
        val expected =
            mapOf(
                MeasurementStatus.REGISTERED to "registered",
                MeasurementStatus.PRELIMINARY to "preliminary",
                MeasurementStatus.FINAL to "final",
                MeasurementStatus.AMENDED to "amended",
                MeasurementStatus.CORRECTED to "corrected",
                MeasurementStatus.CANCELLED to "cancelled",
                MeasurementStatus.ENTERED_IN_ERROR to "entered-in-error",
                MeasurementStatus.UNKNOWN to "unknown",
            )
        assertEquals(MeasurementStatus.entries.toSet(), expected.keys)

        expected.forEach { (status, expectedCode) ->
            val target = measurement(status = status)
            coEvery { measurementService.getMeasurement(target.id, DEFAULT_ORG_NUMBER) } returns
                target

            val observation =
                observationService.getObservationById(
                    ObservationId(target.id.value),
                    DEFAULT_ORG_NUMBER,
                )

            requireNotNull(observation)
            assertEquals(expectedCode, observation.status.value?.getCode())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations returns an empty searchset bundle when the patient has no measurements`() =
        runTest {
            val patientId = PatientId(Uuid.generateV4())
            coEvery {
                measurementService.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)
            } returns emptyList()

            val bundle =
                observationService.searchObservations(
                    patientId = PatientInputId(patientId.value),
                    encounterId = null,
                    code = null,
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(com.google.fhir.model.r4.Bundle.BundleType.Searchset, bundle.type.value)
            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations returns every measurement for the patient without filters`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val first = measurement(patientId = patientId)
        val second = measurement(patientId = patientId)
        coEvery {
            measurementService.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)
        } returns listOf(first, second)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(patientId.value),
                encounterId = null,
                code = null,
                DEFAULT_ORG_NUMBER,
            )

        assertEquals(
            setOf(first.id.value.toString(), second.id.value.toString()),
            bundle.entry.map { (it.resource as Observation).id }.toSet(),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations filters by encounter`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationA = ConsultationId(Uuid.generateV4())
        val consultationB = ConsultationId(Uuid.generateV4())
        val measurementA = measurement(patientId = patientId, consultationId = consultationA)
        val measurementB = measurement(patientId = patientId, consultationId = consultationB)
        coEvery {
            measurementService.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)
        } returns listOf(measurementA, measurementB)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(patientId.value),
                encounterId = EncounterId(consultationA.value),
                code = null,
                DEFAULT_ORG_NUMBER,
            )

        assertEquals(
            measurementA.id.value.toString(),
            (bundle.entry.single().resource as Observation).id,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations filters by LOINC code`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val temperature = measurement(patientId = patientId, loincCode = "8310-5")
        val glucose = measurement(patientId = patientId, loincCode = "2339-0")
        coEvery {
            measurementService.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)
        } returns listOf(temperature, glucose)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(patientId.value),
                encounterId = null,
                code = "2339-0",
                DEFAULT_ORG_NUMBER,
            )

        assertEquals(
            glucose.id.value.toString(),
            (bundle.entry.single().resource as Observation).id,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchObservations combines encounter and code filters`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultationA = ConsultationId(Uuid.generateV4())
        val consultationB = ConsultationId(Uuid.generateV4())
        val match =
            measurement(patientId = patientId, consultationId = consultationA, loincCode = "8310-5")
        val wrongEncounter =
            measurement(patientId = patientId, consultationId = consultationB, loincCode = "8310-5")
        val wrongCode =
            measurement(patientId = patientId, consultationId = consultationA, loincCode = "2339-0")
        coEvery {
            measurementService.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)
        } returns listOf(match, wrongEncounter, wrongCode)

        val bundle =
            observationService.searchObservations(
                patientId = PatientInputId(patientId.value),
                encounterId = EncounterId(consultationA.value),
                code = "8310-5",
                DEFAULT_ORG_NUMBER,
            )

        assertEquals(match.id.value.toString(), (bundle.entry.single().resource as Observation).id)
    }
}
