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
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.tsm.diagnoser.Diagnose

class ConditionService(val konsultasjonService: KonsultasjonService) {

    suspend fun getConditionsByPatientId(patientId: PatientInputId): Bundle {
        val konsultasjoner = konsultasjonService.getKonsultasjoner(PasientId(patientId.value))
        return toBundle(konsultasjoner)
    }

    suspend fun getConditionsByEncounterId(encounterId: EncounterId): Bundle {
        val konsultasjon = konsultasjonService.getKonsultasjon(KonsultasjonId(encounterId.value))
        return toBundle(listOf(konsultasjon))
    }

    private fun toBundle(konsultasjoner: List<Konsultasjon>): Bundle {
        val conditions = konsultasjoner.flatMap { it.toConditions() }
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

    private fun Konsultasjon.toConditions(): List<Condition> = diagnoser.map { diagnose ->
        toCondition(diagnose)
    }

    private fun Konsultasjon.toCondition(diagnose: Diagnose): Condition {
        val oid = "urn:oid:" + diagnose.system.oid()
        return Condition(
            id = conditionFhirId(id, diagnose),
            subject =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(value = "Patient/${pasientId.value}")
                ),
            encounter =
                Reference(
                    reference = com.google.fhir.model.r4.String(value = "Encounter/${id.value}")
                ),
            code =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = oid),
                                code = Code(value = diagnose.code),
                                display = com.google.fhir.model.r4.String(value = diagnose.text),
                            )
                        )
                ),
        )
    }
}
