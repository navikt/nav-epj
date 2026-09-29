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
import com.google.fhir.model.r4.Quantity
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toKotlinLocalDateTime
import no.nav.helse.epj.maaling.Maaling
import no.nav.helse.epj.maaling.MaalingId
import no.nav.helse.epj.maaling.MaalingService
import no.nav.helse.epj.maaling.MaalingStatus
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

private const val LOINC_SYSTEM = "http://loinc.org"
private const val UCUM_SYSTEM = "http://unitsofmeasure.org"

class ObservationService(val maalingService: MaalingService) {

    suspend fun getObservationById(id: ObservationId): Observation? =
        maalingService.getMaaling(MaalingId(id.value))?.toObservation()

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
