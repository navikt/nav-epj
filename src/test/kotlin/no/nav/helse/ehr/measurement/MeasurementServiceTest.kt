package no.nav.helse.ehr.measurement

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class MeasurementServiceTest {
    private val repository = mockk<MeasurementRepository>()
    private val service = MeasurementService(repository)

    @OptIn(ExperimentalUuidApi::class)
    private fun request(
        patientId: PatientId = PatientId(Uuid.generateV4()),
        consultationId: ConsultationId = ConsultationId(Uuid.generateV4()),
    ) =
        CreateMeasurementRequest(
            patientId = patientId,
            consultationId = consultationId,
            hpr = PractitionerHpr("123"),
            loincCode = "8310-5",
            loincDisplay = "Body temperature",
            value = BigDecimal("37.2000"),
            unitCode = "Cel",
            unitDisplay = "degree Celsius",
            effectiveAt = LocalDateTime.now(),
            status = MeasurementStatus.FINAL,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createMeasurement persists a measurement with a generated id`() = runTest {
        val inserted = slot<Measurement>()
        val createRequest = request()
        coEvery { repository.insert(capture(inserted), DEFAULT_ORG_NUMBER) } returns Unit

        val created = service.createMeasurement(createRequest, DEFAULT_ORG_NUMBER)

        assertEquals(createRequest.patientId, created.patientId)
        assertEquals(createRequest.consultationId, created.consultationId)
        assertEquals(createRequest.loincCode, created.loincCode)
        assertEquals(createRequest.value, created.value)
        assertEquals(created, inserted.captured)
        coVerify(exactly = 1) { repository.insert(any(), DEFAULT_ORG_NUMBER) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMeasurement returns the measurement from the repository`() = runTest {
        val id = MeasurementId(Uuid.generateV4())
        val createRequest = request()
        val measurement =
            Measurement(
                id = id,
                patientId = createRequest.patientId,
                consultationId = createRequest.consultationId,
                hpr = createRequest.hpr,
                loincCode = createRequest.loincCode,
                loincDisplay = createRequest.loincDisplay,
                value = createRequest.value,
                unitCode = createRequest.unitCode,
                unitDisplay = createRequest.unitDisplay,
                effectiveAt = createRequest.effectiveAt,
                status = createRequest.status,
            )
        coEvery { repository.findById(id, DEFAULT_ORG_NUMBER) } returns measurement

        val result = service.getMeasurement(id, DEFAULT_ORG_NUMBER)

        assertEquals(measurement, result)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMeasurementsForPatient returns measurements from the repository`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val createRequest = request(patientId = patientId)
        val measurement =
            Measurement(
                id = MeasurementId(Uuid.generateV4()),
                patientId = patientId,
                consultationId = createRequest.consultationId,
                hpr = createRequest.hpr,
                loincCode = createRequest.loincCode,
                loincDisplay = createRequest.loincDisplay,
                value = createRequest.value,
                unitCode = createRequest.unitCode,
                unitDisplay = createRequest.unitDisplay,
                effectiveAt = createRequest.effectiveAt,
                status = createRequest.status,
            )
        coEvery { repository.listByPatientId(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(measurement)

        val result = service.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(measurement), result)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMeasurementsForConsultation returns measurements from the repository`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val createRequest = request(consultationId = consultationId)
        val measurement =
            Measurement(
                id = MeasurementId(Uuid.generateV4()),
                patientId = createRequest.patientId,
                consultationId = consultationId,
                hpr = createRequest.hpr,
                loincCode = createRequest.loincCode,
                loincDisplay = createRequest.loincDisplay,
                value = createRequest.value,
                unitCode = createRequest.unitCode,
                unitDisplay = createRequest.unitDisplay,
                effectiveAt = createRequest.effectiveAt,
                status = createRequest.status,
            )
        coEvery { repository.listByConsultationId(consultationId, DEFAULT_ORG_NUMBER) } returns
            listOf(measurement)

        val result = service.getMeasurementsForConsultation(consultationId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(measurement), result)
    }
}
