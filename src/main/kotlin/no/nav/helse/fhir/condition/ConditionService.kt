package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import no.nav.helse.core.utils.oid
import no.nav.helse.ehr.consultation.Consultation
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.tsm.diagnoser.Diagnose

private const val CONDITION_CATEGORY_SYSTEM =
    "http://terminology.hl7.org/CodeSystem/condition-category"
private const val ENCOUNTER_DIAGNOSIS_CATEGORY = "encounter-diagnosis"

class ConditionService(val consultationService: ConsultationService) {

    suspend fun getConditionsByPatientId(patientId: PatientInputId, orgNumber: String?): Bundle {
        val consultations =
            consultationService.getConsultations(PatientId(patientId.value), orgNumber)
        return toBundle(consultations)
    }

    suspend fun getConditionsByEncounterId(encounterId: EncounterId, orgNumber: String?): Bundle {
        val consultation =
            consultationService.getConsultation(ConsultationId(encounterId.value), orgNumber)
        return toBundle(listOf(consultation))
    }

    suspend fun getConditionsByPatientIdAndEncounterId(
        patientId: PatientInputId,
        encounterId: EncounterId,
        orgNumber: String?,
    ): Bundle {
        val consultations =
            consultationService.getConsultations(PatientId(patientId.value), orgNumber).filter {
                it.id.value == encounterId.value
            }
        return toBundle(consultations)
    }

    private fun toBundle(consultations: List<Consultation>): Bundle {
        val conditions = consultations.flatMap { it.toConditions() }
        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                conditions.map { condition ->
                    Bundle.Entry(
                        fullUrl = Uri(value = "Condition/${condition.id}"),
                        resource = condition,
                    )
                },
        )
    }

    private fun Consultation.toConditions(): List<Condition> = diagnoses.map { diagnosis ->
        toCondition(diagnosis)
    }

    private fun Consultation.toCondition(diagnosis: Diagnose): Condition {
        val oid = "urn:oid:" + diagnosis.system.oid()
        return Condition(
            id = conditionFhirId(id, diagnosis),
            subject =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(value = "Patient/${patientId.value}")
                ),
            encounter =
                Reference(
                    reference = com.google.fhir.model.r4.String(value = "Encounter/${id.value}")
                ),
            category =
                listOf(
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri(value = CONDITION_CATEGORY_SYSTEM),
                                    code = Code(value = ENCOUNTER_DIAGNOSIS_CATEGORY),
                                )
                            )
                    )
                ),
            code =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = oid),
                                code = Code(value = diagnosis.code),
                                display = com.google.fhir.model.r4.String(value = diagnosis.text),
                            )
                        )
                ),
        )
    }
}
