package no.nav.helse.epj.measurement

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.DuplicateMeasurementException
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.consultation.ConsultationRepository
import no.nav.helse.epj.consultation.CreateConsultation
import no.nav.helse.epj.patient.Patient
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.patient.PatientRepository
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.WithPostgresql
import no.nav.helse.utils.ensureOrg
import org.junit.Test

class MeasurementRepositoryTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    val measurementRepository = MeasurementRepository()
    val patientRepository = PatientRepository()
    val consultationRepository = ConsultationRepository()

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun createPatientWithConsultation(
        hpr: PractitionerHpr = PractitionerHpr("123")
    ): Pair<PatientId, ConsultationId> {
        val patientId = PatientId(Uuid.generateV4())
        patientRepository.insert(
            Patient(
                id = patientId,
                givenName = "givenName",
                familyName = "familyName",
                personident = "personident-${patientId.value}",
            )
        )
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
        return patientId to consultationId
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newMeasurement(
        patientId: PatientId,
        consultationId: ConsultationId,
        id: MeasurementId = MeasurementId(Uuid.generateV4()),
        hpr: PractitionerHpr? = PractitionerHpr("123"),
        loincCode: String = "8310-5",
        loincDisplay: String = "Body temperature",
        value: BigDecimal = BigDecimal("37.2000"),
        unitCode: String = "Cel",
        unitDisplay: String = "degree Celsius",
        effectiveAt: LocalDateTime = LocalDateTime.of(2026, 9, 1, 10, 15, 30, 500_000),
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
    fun `findById returns null when measurement does not exist`() = runTest {
        assertNull(
            measurementRepository.findById(MeasurementId(Uuid.generateV4()), DEFAULT_ORG_NUMBER)
        )
    }

    @Test
    fun `insert stores a complete round trip of a measurement`() = runTest {
        val (patientId, consultationId) = createPatientWithConsultation()
        val measurement = newMeasurement(patientId, consultationId)

        measurementRepository.insert(measurement, DEFAULT_ORG_NUMBER)
        val funnet = measurementRepository.findById(measurement.id, DEFAULT_ORG_NUMBER)

        assertEquals(measurement.id, funnet?.id)
        assertEquals(measurement.patientId, funnet?.patientId)
        assertEquals(measurement.consultationId, funnet?.consultationId)
        assertEquals(measurement.hpr, funnet?.hpr)
        assertEquals(measurement.loincCode, funnet?.loincCode)
        assertEquals(measurement.loincDisplay, funnet?.loincDisplay)
        assertEquals(0, measurement.value.compareTo(funnet?.value))
        assertEquals(measurement.unitCode, funnet?.unitCode)
        assertEquals(measurement.unitDisplay, funnet?.unitDisplay)
        assertEquals(measurement.effectiveAt, funnet?.effectiveAt)
        assertEquals(measurement.status, funnet?.status)
    }

    @Test
    fun `insert preserves decimal precision, unit, code and status values`() = runTest {
        val (patientId, consultationId) = createPatientWithConsultation()
        val measurement =
            newMeasurement(
                patientId,
                consultationId,
                loincCode = "2339-0",
                loincDisplay = "Glucose",
                value = BigDecimal("5.6789"),
                unitCode = "mmol/L",
                unitDisplay = "millimole per liter",
                status = MeasurementStatus.ENTERED_IN_ERROR,
            )

        measurementRepository.insert(measurement, DEFAULT_ORG_NUMBER)
        val funnet = measurementRepository.findById(measurement.id, DEFAULT_ORG_NUMBER)

        assertEquals(0, BigDecimal("5.6789").compareTo(funnet?.value))
        assertEquals("mmol/L", funnet?.unitCode)
        assertEquals("millimole per liter", funnet?.unitDisplay)
        assertEquals("2339-0", funnet?.loincCode)
        assertEquals(MeasurementStatus.ENTERED_IN_ERROR, funnet?.status)
    }

    @Test
    fun `insert without a performer stores a null hpr`() = runTest {
        val (patientId, consultationId) = createPatientWithConsultation()
        val measurement = newMeasurement(patientId, consultationId, hpr = null)

        measurementRepository.insert(measurement, DEFAULT_ORG_NUMBER)
        val funnet = measurementRepository.findById(measurement.id, DEFAULT_ORG_NUMBER)

        assertNull(funnet?.hpr)
    }

    @Test
    fun `listByPatientId returns only measurements for the correct patient ordered by effectiveAt`() =
        runTest {
            val (patientId, consultationId) = createPatientWithConsultation()
            val (otherPatientId, otherConsultationId) = createPatientWithConsultation()

            val eldst =
                newMeasurement(
                    patientId,
                    consultationId,
                    effectiveAt = LocalDateTime.now().minusDays(2),
                )
            val nyest = newMeasurement(patientId, consultationId, effectiveAt = LocalDateTime.now())
            val mellom =
                newMeasurement(
                    patientId,
                    consultationId,
                    effectiveAt = LocalDateTime.now().minusDays(1),
                )
            val otherPatientMeasurement = newMeasurement(otherPatientId, otherConsultationId)

            listOf(nyest, eldst, mellom, otherPatientMeasurement).forEach {
                measurementRepository.insert(it, DEFAULT_ORG_NUMBER)
            }

            val measurements = measurementRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)

            assertEquals(listOf(eldst.id, mellom.id, nyest.id), measurements.map { it.id })
        }

    @Test
    fun `listByConsultationId returns only measurements for the correct consultation ordered by effectiveAt`() =
        runTest {
            val (patientId, consultationId) = createPatientWithConsultation()
            val otherConsultationId =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        emptyList(),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            val eldst =
                newMeasurement(
                    patientId,
                    consultationId,
                    effectiveAt = LocalDateTime.now().minusDays(1),
                )
            val nyest = newMeasurement(patientId, consultationId, effectiveAt = LocalDateTime.now())
            val otherConsultationMeasurement = newMeasurement(patientId, otherConsultationId)

            listOf(nyest, eldst, otherConsultationMeasurement).forEach {
                measurementRepository.insert(it, DEFAULT_ORG_NUMBER)
            }

            val measurements =
                measurementRepository.listByConsultationId(consultationId, DEFAULT_ORG_NUMBER)

            assertEquals(listOf(eldst.id, nyest.id), measurements.map { it.id })
        }

    @Test
    fun `insert with the same id twice throws DuplicateMeasurementException and leaves data unchanged`() =
        runTest {
            val (patientId, consultationId) = createPatientWithConsultation()
            val measurement = newMeasurement(patientId, consultationId, loincCode = "8310-5")

            measurementRepository.insert(measurement, DEFAULT_ORG_NUMBER)

            assertFailsWith<DuplicateMeasurementException> {
                measurementRepository.insert(
                    measurement.copy(loincCode = "annen-kode"),
                    DEFAULT_ORG_NUMBER,
                )
            }

            val funnet = measurementRepository.findById(measurement.id, DEFAULT_ORG_NUMBER)
            assertEquals("8310-5", funnet?.loincCode)
        }

    @Test
    fun `insert rejects a measurement whose consultation belongs to another patient`() = runTest {
        val (_, consultationId) = createPatientWithConsultation()
        val (otherPatientId, _) = createPatientWithConsultation()
        val measurement = newMeasurement(otherPatientId, consultationId)

        assertFailsWith<ConsultationBelongsToOtherPatientException> {
            measurementRepository.insert(measurement, DEFAULT_ORG_NUMBER)
        }

        assertNull(measurementRepository.findById(measurement.id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `measurements recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val (patientId, consultationId) = createPatientWithConsultation()
        val measurement = newMeasurement(patientId, consultationId)
        measurementRepository.insert(measurement, DEFAULT_ORG_NUMBER)

        assertNull(measurementRepository.findById(measurement.id, OTHER_ORG_NUMBER))
        assertEquals(0, measurementRepository.listByPatientId(patientId, OTHER_ORG_NUMBER).size)
        assertEquals(
            0,
            measurementRepository.listByConsultationId(consultationId, OTHER_ORG_NUMBER).size,
        )
        assertEquals(1, measurementRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).size)
        assertEquals(measurement.id, measurementRepository.findById(measurement.id, null)?.id)
    }

    @Test
    fun `insert rejects a measurement for a consultation at another org`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val (patientId, consultationId) = createPatientWithConsultation()
        val measurement = newMeasurement(patientId, consultationId)

        assertTrue(
            runCatching { measurementRepository.insert(measurement, OTHER_ORG_NUMBER) }.isFailure
        )
        assertNull(measurementRepository.findById(measurement.id, null))
    }
}
