package no.nav.helse.epj.measurement

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
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
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
        val opprettRequest = request()
        coEvery { repository.insert(capture(inserted), DEFAULT_ORG_NUMBER) } returns Unit

        val opprettet = service.createMeasurement(opprettRequest, DEFAULT_ORG_NUMBER)

        assertEquals(opprettRequest.patientId, opprettet.patientId)
        assertEquals(opprettRequest.consultationId, opprettet.consultationId)
        assertEquals(opprettRequest.loincCode, opprettet.loincCode)
        assertEquals(opprettRequest.value, opprettet.value)
        assertEquals(opprettet, inserted.captured)
        coVerify(exactly = 1) { repository.insert(any(), DEFAULT_ORG_NUMBER) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMeasurement returns the measurement from the repository`() = runTest {
        val id = MeasurementId(Uuid.generateV4())
        val opprettRequest = request()
        val measurement =
            Measurement(
                id = id,
                patientId = opprettRequest.patientId,
                consultationId = opprettRequest.consultationId,
                hpr = opprettRequest.hpr,
                loincCode = opprettRequest.loincCode,
                loincDisplay = opprettRequest.loincDisplay,
                value = opprettRequest.value,
                unitCode = opprettRequest.unitCode,
                unitDisplay = opprettRequest.unitDisplay,
                effectiveAt = opprettRequest.effectiveAt,
                status = opprettRequest.status,
            )
        coEvery { repository.findById(id, DEFAULT_ORG_NUMBER) } returns measurement

        val resultat = service.getMeasurement(id, DEFAULT_ORG_NUMBER)

        assertEquals(measurement, resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMeasurementsForPatient returns measurements from the repository`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val opprettRequest = request(patientId = patientId)
        val measurement =
            Measurement(
                id = MeasurementId(Uuid.generateV4()),
                patientId = patientId,
                consultationId = opprettRequest.consultationId,
                hpr = opprettRequest.hpr,
                loincCode = opprettRequest.loincCode,
                loincDisplay = opprettRequest.loincDisplay,
                value = opprettRequest.value,
                unitCode = opprettRequest.unitCode,
                unitDisplay = opprettRequest.unitDisplay,
                effectiveAt = opprettRequest.effectiveAt,
                status = opprettRequest.status,
            )
        coEvery { repository.listByPatientId(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(measurement)

        val resultat = service.getMeasurementsForPatient(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(measurement), resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMeasurementsForConsultation returns measurements from the repository`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val opprettRequest = request(consultationId = consultationId)
        val measurement =
            Measurement(
                id = MeasurementId(Uuid.generateV4()),
                patientId = opprettRequest.patientId,
                consultationId = consultationId,
                hpr = opprettRequest.hpr,
                loincCode = opprettRequest.loincCode,
                loincDisplay = opprettRequest.loincDisplay,
                value = opprettRequest.value,
                unitCode = opprettRequest.unitCode,
                unitDisplay = opprettRequest.unitDisplay,
                effectiveAt = opprettRequest.effectiveAt,
                status = opprettRequest.status,
            )
        coEvery { repository.listByConsultationId(consultationId, DEFAULT_ORG_NUMBER) } returns
            listOf(measurement)

        val resultat = service.getMeasurementsForConsultation(consultationId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(measurement), resultat)
    }
}
