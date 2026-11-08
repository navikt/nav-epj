package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Period
import com.google.fhir.model.r4.PositiveInt
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toKotlinLocalDateTime
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.oid
import no.nav.helse.ehr.consultation.Consultation
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.fhir.condition.conditionFhirId
import no.nav.helse.fhir.patient.PatientInputId

class EncounterService(val consultationService: ConsultationService) {

    suspend fun getEncounterById(encounterId: EncounterId, orgNumber: String?): Encounter {
        val consultation =
            consultationService.getConsultation(ConsultationId(encounterId.value), orgNumber)
        return consultation.toEncounter()
    }

    suspend fun getActiveEncounterByPatient(
        patientId: PatientInputId,
        hpr: PractitionerHpr,
        orgNumber: String?,
    ): Encounter? {
        val consultation =
            consultationService.getActiveConsultation(PatientId(patientId.value), hpr, orgNumber)
        return consultation?.toEncounter()
    }

    suspend fun getEncountersByPatient(patientId: PatientInputId, orgNumber: String?): Bundle {
        val consultations =
            consultationService.getConsultations(PatientId(patientId.value), orgNumber)
        if (consultations.isEmpty()) {
            return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
        }
        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                consultations.map { consultation ->
                    val encounter = consultation.toEncounter()
                    Bundle.Entry(
                        fullUrl = Uri(value = "Encounter/${encounter.id}"),
                        resource = encounter,
                    )
                },
        )
    }

    fun Consultation.toEncounter(): Encounter {
        val status =
            when (this.status) {
                ConsultationStatus.PLANNED -> Encounter.EncounterStatus.Planned
                ConsultationStatus.IN_PROGRESS -> Encounter.EncounterStatus.In_Progress
                ConsultationStatus.FINISHED -> Encounter.EncounterStatus.Finished
                ConsultationStatus.CANCELLED -> Encounter.EncounterStatus.Cancelled
            }
        return Encounter(
            id = this.id.value.toString(),
            subject =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(value = "Patient/${this.patientId.value}")
                ),
            participant =
                this.hpr.map {
                    Encounter.Participant(
                        individual =
                            Reference(
                                reference =
                                    com.google.fhir.model.r4.String(value = "Practitioner/$it")
                            )
                    )
                },
            reasonCode =
                this.diagnoses.map { diagnosis ->
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri(value = "urn:oid:" + diagnosis.system.oid()),
                                    code = Code(value = diagnosis.code),
                                    display =
                                        com.google.fhir.model.r4.String(value = diagnosis.text),
                                )
                            )
                    )
                },
            diagnosis =
                this.diagnoses.mapIndexed { index, diagnosis ->
                    Encounter.Diagnosis(
                        condition =
                            Reference(
                                reference =
                                    com.google.fhir.model.r4.String(
                                        value = "Condition/${conditionFhirId(this.id, diagnosis)}"
                                    )
                            ),
                        rank = PositiveInt(value = index + 1),
                    )
                },
            period =
                Period(
                    start =
                        DateTime(
                            value =
                                FhirDateTime.DateTime(
                                    this.startedAt.toKotlinLocalDateTime(),
                                    UtcOffset.ZERO,
                                )
                        ),
                    end =
                        this.endedAt?.let {
                            DateTime(
                                value =
                                    FhirDateTime.DateTime(
                                        it.toKotlinLocalDateTime(),
                                        UtcOffset.ZERO,
                                    )
                            )
                        },
                ),
            status = Enumeration(value = status),
            `class` =
                Coding(
                    code = Code(value = "VR"),
                    system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
                ),
            serviceProvider =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(
                            value = "Organization/${this.clinicId.value}"
                        )
                ),
        )
    }
}
