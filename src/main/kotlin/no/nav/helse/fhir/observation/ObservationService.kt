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
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.maaling.Maaling
import no.nav.helse.epj.maaling.MaalingId
import no.nav.helse.epj.maaling.MaalingService
import no.nav.helse.epj.maaling.MaalingStatus
import no.nav.helse.epj.maaling.OpprettMaalingRequest
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

private const val LOINC_SYSTEM = "http://loinc.org"
private const val UCUM_SYSTEM = "http://unitsofmeasure.org"

/**
 * Raised when an incoming Observation does not fit the narrow scalar shape backed by [Maaling].
 * Carries the FHIR [OperationOutcome.IssueType] so the route can turn it into a matching
 * OperationOutcome without re-deriving the classification.
 */
class InvalidObservationException(val issueType: OperationOutcome.IssueType, message: String) :
    RuntimeException(message)

class ObservationService(val maalingService: MaalingService) {

    suspend fun getObservationById(id: ObservationId): Observation? =
        maalingService.getMaaling(MaalingId(id.value))?.toObservation()

    suspend fun createObservation(request: OpprettMaalingRequest): Observation =
        maalingService.opprettMaaling(request).toObservation()

    suspend fun searchObservations(
        patientId: PatientInputId,
        encounterId: EncounterId?,
        code: String?,
    ): Bundle {
        val maalinger =
            maalingService
                .getMaalingerForPasient(PasientId(patientId.value))
                .filter { encounterId == null || it.konsultasjonId.value == encounterId.value }
                .filter { code == null || it.loincKode == code }

        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                maalinger.map { maaling ->
                    val observation = maaling.toObservation()
                    Bundle.Entry(
                        fullUrl = Uri(value = "Observation/${observation.id}"),
                        resource = observation,
                    )
                },
        )
    }

    fun Maaling.toObservation(): Observation =
        Observation(
            id = this.id.value.toString(),
            status = Enumeration(value = this.status.toObservationStatus()),
            code =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = LOINC_SYSTEM),
                                code = Code(value = this.loincKode),
                                display =
                                    com.google.fhir.model.r4.String(value = this.loincVisningsnavn),
                            )
                        )
                ),
            subject =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(value = "Patient/${this.pasientId.value}")
                ),
            encounter =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(
                            value = "Encounter/${this.konsultasjonId.value}"
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
                                this.effektivTidspunkt.toKotlinLocalDateTime(),
                                UtcOffset.ZERO,
                            )
                    )
                ),
            value =
                Observation.Value.Quantity(
                    Quantity(
                        value = Decimal(value = this.verdi.toPlainString().toBigDecimal()),
                        unit = com.google.fhir.model.r4.String(value = this.enhetVisningsnavn),
                        system = Uri(value = UCUM_SYSTEM),
                        code = Code(value = this.enhetKode),
                    )
                ),
        )

    private fun MaalingStatus.toObservationStatus(): Observation.ObservationStatus =
        when (this) {
            MaalingStatus.REGISTERED -> Observation.ObservationStatus.Registered
            MaalingStatus.PRELIMINARY -> Observation.ObservationStatus.Preliminary
            MaalingStatus.FINAL -> Observation.ObservationStatus.Final
            MaalingStatus.AMENDED -> Observation.ObservationStatus.Amended
            MaalingStatus.CORRECTED -> Observation.ObservationStatus.Corrected
            MaalingStatus.CANCELLED -> Observation.ObservationStatus.Cancelled
            MaalingStatus.ENTERED_IN_ERROR -> Observation.ObservationStatus.Entered_In_Error
            MaalingStatus.UNKNOWN -> Observation.ObservationStatus.Unknown
        }
}

private fun Observation.ObservationStatus.toMaalingStatus(): MaalingStatus =
    when (this) {
        Observation.ObservationStatus.Registered -> MaalingStatus.REGISTERED
        Observation.ObservationStatus.Preliminary -> MaalingStatus.PRELIMINARY
        Observation.ObservationStatus.Final -> MaalingStatus.FINAL
        Observation.ObservationStatus.Amended -> MaalingStatus.AMENDED
        Observation.ObservationStatus.Corrected -> MaalingStatus.CORRECTED
        Observation.ObservationStatus.Cancelled -> MaalingStatus.CANCELLED
        Observation.ObservationStatus.Entered_In_Error -> MaalingStatus.ENTERED_IN_ERROR
        Observation.ObservationStatus.Unknown -> MaalingStatus.UNKNOWN
    }

private fun invalid(issueType: OperationOutcome.IssueType, message: String): Nothing =
    throw InvalidObservationException(issueType, message)

/**
 * Validates and maps an incoming create request to the narrow scalar shape backed by [Maaling]. The
 * client-supplied `id`, if any, is intentionally never read here: create always assigns a
 * server-generated id, per FHIR create semantics.
 */
fun Observation.toOpprettMaalingRequest(): OpprettMaalingRequest {
    rejectUnsupportedFields(this)

    val status =
        this.status.value?.toMaalingStatus()
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
    val loincKode =
        coding.code?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.code.coding.code er påkrevd",
            )
    val loincVisningsnavn =
        coding.display?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.code.coding.display er påkrevd",
            )

    val pasientId = parseReferenceId(this.subject?.reference?.value, "Patient", "subject")
    val konsultasjonId =
        parseReferenceId(this.encounter?.reference?.value, "Encounter", "encounter")
    val hpr = parsePerformer(this.performer)
    val effektivTidspunkt = parseEffectiveDateTime(this.effective)

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
    val verdi =
        quantity.value?.value?.let { BigDecimal(it.toPlainString()) }
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.valueQuantity.value er påkrevd",
            )
    if (quantity.system?.value != UCUM_SYSTEM) {
        invalid(
            OperationOutcome.IssueType.Value,
            "Observation.valueQuantity.system må være '$UCUM_SYSTEM'",
        )
    }
    val enhetKode =
        quantity.code?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.valueQuantity.code er påkrevd",
            )
    val enhetVisningsnavn =
        quantity.unit?.value
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "Observation.valueQuantity.unit er påkrevd",
            )

    return OpprettMaalingRequest(
        pasientId = PasientId(pasientId),
        konsultasjonId = KonsultasjonId(konsultasjonId),
        hpr = hpr,
        loincKode = loincKode,
        loincVisningsnavn = loincVisningsnavn,
        verdi = verdi,
        enhetKode = enhetKode,
        enhetVisningsnavn = enhetVisningsnavn,
        effektivTidspunkt = effektivTidspunkt,
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

private fun parsePerformer(performer: List<Reference>): HelsepersonellHpr? {
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
    return HelsepersonellHpr(reference.removePrefix(prefix))
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
 * Rejects every Observation element that [Maaling] cannot represent, so unsupported content is
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
