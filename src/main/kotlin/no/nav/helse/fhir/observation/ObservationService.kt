package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Decimal
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Observation
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Quantity
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import java.math.BigDecimal
import kotlin.uuid.Uuid
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.measurement.CreateMeasurementRequest
import no.nav.helse.ehr.measurement.Measurement
import no.nav.helse.ehr.measurement.MeasurementId
import no.nav.helse.ehr.measurement.MeasurementService
import no.nav.helse.ehr.measurement.MeasurementStatus
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

private const val LOINC_SYSTEM = "http://loinc.org"
private const val MEASUREMENT_MAX_DECIMALS = 4

private const val MEASUREMENT_MAX_INTEGER_DIGITS = 8

private const val UCUM_SYSTEM = "http://unitsofmeasure.org"

/**
 * Raised when an incoming Observation does not fit the narrow scalar shape backed by [Measurement].
 * Carries the FHIR [OperationOutcome.IssueType] so the route can turn it into a matching
 * OperationOutcome without re-deriving the classification.
 */
class InvalidObservationException(val issueType: OperationOutcome.IssueType, message: String) :
    RuntimeException(message)

class ObservationService(val measurementService: MeasurementService) {

    suspend fun getObservationById(id: ObservationId, orgNumber: String?): Observation? =
        measurementService.getMeasurement(MeasurementId(id.value), orgNumber)?.toObservation()

    suspend fun createObservation(
        request: CreateMeasurementRequest,
        orgNumber: String,
    ): Observation = measurementService.createMeasurement(request, orgNumber).toObservation()

    suspend fun searchObservations(
        patientId: PatientInputId,
        encounterId: EncounterId?,
        code: String?,
        orgNumber: String?,
    ): Bundle {
        val measurements =
            measurementService
                .getMeasurementsForPatient(PatientId(patientId.value), orgNumber)
                .filter { encounterId == null || it.consultationId.value == encounterId.value }
                .filter { code == null || it.loincCode == code }

        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                measurements.map { measurement ->
                    val observation = measurement.toObservation()
                    Bundle.Entry(
                        fullUrl = Uri(value = "Observation/${observation.id}"),
                        resource = observation,
                    )
                },
        )
    }

    fun Measurement.toObservation(): Observation =
        Observation(
            id = this.id.value.toString(),
            status = Enumeration(value = this.status.toObservationStatus()),
            code =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = LOINC_SYSTEM),
                                code = Code(value = this.loincCode),
                                display = com.google.fhir.model.r4.String(value = this.loincDisplay),
                            )
                        )
                ),
            subject =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(value = "Patient/${this.patientId.value}")
                ),
            encounter =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(
                            value = "Encounter/${this.consultationId.value}"
                        )
                ),
            performer =
                this.hpr?.let {
                    listOf(
                        Reference(
                            reference =
                                com.google.fhir.model.r4.String(value = "Practitioner/${it.value}")
                        )
                    )
                } ?: emptyList(),
            effective =
                Observation.Effective.DateTime(
                    DateTime(
                        value =
                            FhirDateTime.DateTime(
                                this.effectiveAt.toKotlinLocalDateTime(),
                                UtcOffset.ZERO,
                            )
                    )
                ),
            value =
                Observation.Value.Quantity(
                    Quantity(
                        value = Decimal(value = this.value.toPlainString().toBigDecimal()),
                        unit = com.google.fhir.model.r4.String(value = this.unitDisplay),
                        system = Uri(value = UCUM_SYSTEM),
                        code = Code(value = this.unitCode),
                    )
                ),
        )

    private fun MeasurementStatus.toObservationStatus(): Observation.ObservationStatus =
        when (this) {
            MeasurementStatus.REGISTERED -> Observation.ObservationStatus.Registered
            MeasurementStatus.PRELIMINARY -> Observation.ObservationStatus.Preliminary
            MeasurementStatus.FINAL -> Observation.ObservationStatus.Final
            MeasurementStatus.AMENDED -> Observation.ObservationStatus.Amended
            MeasurementStatus.CORRECTED -> Observation.ObservationStatus.Corrected
            MeasurementStatus.CANCELLED -> Observation.ObservationStatus.Cancelled
            MeasurementStatus.ENTERED_IN_ERROR -> Observation.ObservationStatus.Entered_In_Error
            MeasurementStatus.UNKNOWN -> Observation.ObservationStatus.Unknown
        }
}

private fun Observation.ObservationStatus.toMeasurementStatus(): MeasurementStatus =
    when (this) {
        Observation.ObservationStatus.Registered -> MeasurementStatus.REGISTERED
        Observation.ObservationStatus.Preliminary -> MeasurementStatus.PRELIMINARY
        Observation.ObservationStatus.Final -> MeasurementStatus.FINAL
        Observation.ObservationStatus.Amended -> MeasurementStatus.AMENDED
        Observation.ObservationStatus.Corrected -> MeasurementStatus.CORRECTED
        Observation.ObservationStatus.Cancelled -> MeasurementStatus.CANCELLED
        Observation.ObservationStatus.Entered_In_Error -> MeasurementStatus.ENTERED_IN_ERROR
        Observation.ObservationStatus.Unknown -> MeasurementStatus.UNKNOWN
    }

private fun invalid(issueType: OperationOutcome.IssueType, message: String): Nothing =
    throw InvalidObservationException(issueType, message)

/**
 * Validates and maps an incoming create request to the narrow scalar shape backed by [Measurement].
 * The client-supplied `id`, if any, is intentionally never read here: create always assigns a
 * server-generated id, per FHIR create semantics.
 */
fun Observation.toCreateMeasurementRequest(): CreateMeasurementRequest {
    rejectUnsupportedFields(this)

    val status =
        this.status.value?.toMeasurementStatus()
            ?: invalid(OperationOutcome.IssueType.Code_Invalid, "Observation.status er ugyldig")

    val codings = this.code.coding
    if (codings.size != 1) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "Observation.code må inneholde nøyaktig én coding",
        )
    }
    val coding = codings.single()
    if (coding.system?.value != LOINC_SYSTEM) {
        invalid(
            OperationOutcome.IssueType.Value,
            "Observation.code.coding.system må være '$LOINC_SYSTEM'",
        )
    }
    val loincCode =
        coding.code?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.code.coding.code er påkrevd",
            )
    val loincDisplay =
        coding.display?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.code.coding.display er påkrevd",
            )

    val patientId = parseReferenceId(this.subject?.reference?.value, "Patient", "subject")
    val consultationId =
        parseReferenceId(this.encounter?.reference?.value, "Encounter", "encounter")
    val hpr = parsePerformer(this.performer)
    val effectiveAt = parseEffectiveDateTime(this.effective)

    val quantity =
        when (val value = this.value) {
            is Observation.Value.Quantity -> value.value
            null ->
                invalid(OperationOutcome.IssueType.Required, "Observation.valueQuantity er påkrevd")
            else ->
                invalid(
                    OperationOutcome.IssueType.Not_Supported,
                    "Observation.value støtter kun Quantity, ikke ${value::class.simpleName}",
                )
        }
    if (quantity.comparator != null) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "Observation.valueQuantity.comparator støttes ikke",
        )
    }
    val value =
        quantity.value?.value?.let { BigDecimal(it.toPlainString()) }
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.valueQuantity.value er påkrevd",
            )
    val normalizedValue = value.stripTrailingZeros()
    if (
        normalizedValue.scale() > MEASUREMENT_MAX_DECIMALS ||
            normalizedValue.precision() - normalizedValue.scale() > MEASUREMENT_MAX_INTEGER_DIGITS
    ) {
        invalid(
            OperationOutcome.IssueType.Value,
            "Observation.valueQuantity.value støtter maks $MEASUREMENT_MAX_INTEGER_DIGITS " +
                "heltallssiffer og $MEASUREMENT_MAX_DECIMALS desimaler",
        )
    }
    if (quantity.system?.value != UCUM_SYSTEM) {
        invalid(
            OperationOutcome.IssueType.Value,
            "Observation.valueQuantity.system må være '$UCUM_SYSTEM'",
        )
    }
    val unitCode =
        quantity.code?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.valueQuantity.code er påkrevd",
            )
    val unitDisplay =
        quantity.unit?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.valueQuantity.unit er påkrevd",
            )

    return CreateMeasurementRequest(
        patientId = PatientId(patientId),
        consultationId = ConsultationId(consultationId),
        hpr = hpr,
        loincCode = loincCode,
        loincDisplay = loincDisplay,
        value = value,
        unitCode = unitCode,
        unitDisplay = unitDisplay,
        effectiveAt = effectiveAt,
        status = status,
    )
}

private fun parseReferenceId(reference: String?, resourceType: String, field: String): Uuid {
    val prefix = "$resourceType/"
    if (reference == null) {
        invalid(OperationOutcome.IssueType.Required, "Observation.$field er påkrevd")
    }
    if (!reference.startsWith(prefix)) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "Observation.$field.reference må starte med '$prefix'",
        )
    }
    return try {
        Uuid.parse(reference.removePrefix(prefix))
    } catch (exception: IllegalArgumentException) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "Observation.$field.reference er ikke en gyldig UUID: ${exception.message}",
        )
    }
}

private fun parsePerformer(performer: List<Reference>): PractitionerHpr? {
    if (performer.isEmpty()) return null
    if (performer.size > 1) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "Observation.performer støtter maks én referanse",
        )
    }
    val reference =
        performer.single().reference?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.performer.reference er påkrevd når performer er oppgitt",
            )
    val prefix = "Practitioner/"
    if (!reference.startsWith(prefix) || reference == prefix) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "Observation.performer støtter kun Practitioner-referanser",
        )
    }
    return PractitionerHpr(reference.removePrefix(prefix))
}

private fun parseEffectiveDateTime(effective: Observation.Effective?): java.time.LocalDateTime {
    val dateTime =
        (effective as? Observation.Effective.DateTime)
            ?: invalid(
                OperationOutcome.IssueType.Not_Supported,
                "Observation.effective må være effectiveDateTime",
            )
    val fhirDateTime =
        dateTime.value.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.effectiveDateTime er påkrevd",
            )
    val dateTimeValue =
        (fhirDateTime as? FhirDateTime.DateTime)
            ?: invalid(
                OperationOutcome.IssueType.Invalid,
                "Observation.effectiveDateTime må inneholde klokkeslett, ikke bare dato",
            )
    if (dateTimeValue.utcOffset != UtcOffset.ZERO) {
        invalid(OperationOutcome.IssueType.Value, "Observation.effectiveDateTime må være i UTC (Z)")
    }
    return dateTimeValue.dateTime.toJavaLocalDateTime()
}

/**
 * Rejects every Observation element that [Measurement] cannot represent, so unsupported content is
 * never silently discarded on create.
 */
private fun rejectUnsupportedFields(observation: Observation) {
    val unsupported = buildList {
        if (observation.identifier.isNotEmpty()) add("identifier")
        if (observation.basedOn.isNotEmpty()) add("basedOn")
        if (observation.partOf.isNotEmpty()) add("partOf")
        if (observation.category.isNotEmpty()) add("category")
        if (observation.focus.isNotEmpty()) add("focus")
        if (observation.issued != null) add("issued")
        if (observation.dataAbsentReason != null) add("dataAbsentReason")
        if (observation.interpretation.isNotEmpty()) add("interpretation")
        if (observation.note.isNotEmpty()) add("note")
        if (observation.bodySite != null) add("bodySite")
        if (observation.method != null) add("method")
        if (observation.specimen != null) add("specimen")
        if (observation.device != null) add("device")
        if (observation.referenceRange.isNotEmpty()) add("referenceRange")
        if (observation.hasMember.isNotEmpty()) add("hasMember")
        if (observation.derivedFrom.isNotEmpty()) add("derivedFrom")
        if (observation.component.isNotEmpty()) add("component")
        if (observation.extension.isNotEmpty()) add("extension")
        if (observation.modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    if (unsupported.isNotEmpty()) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "Observation.${unsupported.joinToString(", ")} støttes ikke",
        )
    }
}
