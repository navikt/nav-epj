package no.nav.helse.ehr.measurement

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId

class MeasurementService(private val measurementRepository: MeasurementRepository) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createMeasurement(
        request: CreateMeasurementRequest,
        orgNumber: String,
    ): Measurement {
        val newMeasurement =
            Measurement(
                id = MeasurementId(Uuid.generateV4()),
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

        measurementRepository.insert(newMeasurement, orgNumber)
        return newMeasurement
    }

    suspend fun getMeasurement(id: MeasurementId, orgNumber: String?): Measurement? {
        return measurementRepository.findById(id, orgNumber)
    }

    suspend fun getMeasurementsForPatient(
        patientId: PatientId,
        orgNumber: String?,
    ): List<Measurement> {
        return measurementRepository.listByPatientId(patientId, orgNumber)
    }

    suspend fun getMeasurementsForConsultation(
        consultationId: ConsultationId,
        orgNumber: String?,
    ): List<Measurement> {
        return measurementRepository.listByConsultationId(consultationId, orgNumber)
    }
}
