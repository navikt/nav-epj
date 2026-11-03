package no.nav.helse.ehr.measurement

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr

@JvmInline value class MeasurementId(val value: Uuid)

data class Measurement(
    val id: MeasurementId,
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val hpr: PractitionerHpr?,
    val loincCode: String,
    val loincDisplay: String,
    val value: BigDecimal,
    val unitCode: String,
    val unitDisplay: String,
    val effectiveAt: LocalDateTime,
    val status: MeasurementStatus,
)

data class CreateMeasurementRequest(
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val hpr: PractitionerHpr?,
    val loincCode: String,
    val loincDisplay: String,
    val value: BigDecimal,
    val unitCode: String,
    val unitDisplay: String,
    val effectiveAt: LocalDateTime,
    val status: MeasurementStatus,
)

/**
 * Mirrors the FHIR Observation `status` value set (registered | preliminary | final | amended |
 * corrected | cancelled | entered-in-error | unknown) so later Observation mapping is a direct name
 * translation rather than a reinterpretation.
 */
enum class MeasurementStatus {
    REGISTERED,
    PRELIMINARY,
    FINAL,
    AMENDED,
    CORRECTED,
    CANCELLED,
    ENTERED_IN_ERROR,
    UNKNOWN,
}
