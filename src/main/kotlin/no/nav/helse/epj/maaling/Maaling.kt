package no.nav.helse.epj.maaling

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr

@JvmInline value class MaalingId(val value: Uuid)

data class Maaling(
    val id: MaalingId,
    val patientId: PatientId,
    val konsultasjonId: KonsultasjonId,
    val hpr: PractitionerHpr?,
    val loincKode: String,
    val loincVisningsnavn: String,
    val verdi: BigDecimal,
    val enhetKode: String,
    val enhetVisningsnavn: String,
    val effektivTidspunkt: LocalDateTime,
    val status: MaalingStatus,
)

data class OpprettMaalingRequest(
    val patientId: PatientId,
    val konsultasjonId: KonsultasjonId,
    val hpr: PractitionerHpr?,
    val loincKode: String,
    val loincVisningsnavn: String,
    val verdi: BigDecimal,
    val enhetKode: String,
    val enhetVisningsnavn: String,
    val effektivTidspunkt: LocalDateTime,
    val status: MaalingStatus,
)

/**
 * Mirrors the FHIR Observation `status` value set (registered | preliminary | final | amended |
 * corrected | cancelled | entered-in-error | unknown) so later Observation mapping is a direct name
 * translation rather than a reinterpretation.
 */
enum class MaalingStatus {
    REGISTERED,
    PRELIMINARY,
    FINAL,
    AMENDED,
    CORRECTED,
    CANCELLED,
    ENTERED_IN_ERROR,
    UNKNOWN,
}
