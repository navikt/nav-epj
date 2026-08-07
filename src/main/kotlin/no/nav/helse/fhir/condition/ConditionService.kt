package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.nav.helse.epj.konsultasjon.Diagnose
import no.nav.helse.epj.konsultasjon.DiagnoseSystem
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

class ConditionService(private val epjClient: HttpClient) {

  suspend fun getConditionsByPatientId(patientId: PatientInputId): Bundle {
    val httpResponse = epjClient.get("/api/diagnoser?patientId=${patientId.value}")
    if (httpResponse.status.value == 200) {
      val diagnoser = httpResponse.body<List<Diagnose>>()
      if (diagnoser.isEmpty()) {
        return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
      }
      return toBundle(diagnoser, null)
    }

    return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
  }

  suspend fun getConditionsByEncounterId(encounterId: EncounterId): Bundle {
    val httpResponse = epjClient.get("/api/diagnoser?konsultasjonId=${encounterId.value}")
    if (httpResponse.status.value == 200) {
      val diagnoser = httpResponse.body<List<Diagnose>>()
      if (diagnoser.isEmpty()) {
        return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
      }
      return toBundle(diagnoser, encounterId)
    }

    return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
  }

  private fun toBundle(diagnoser: List<Diagnose>, encounterId: EncounterId?): Bundle {
    val conditions = diagnoser.toCondition(encounterId)
    val bundle =
      Bundle(
        type = Enumeration(value = Bundle.BundleType.Searchset),
        entry =
          conditions.map { condition ->
            Bundle.Entry(fullUrl = Uri(value = "Condition/${condition.id}"), resource = condition)
          },
      )
    return bundle
  }

  private fun List<Diagnose>.toCondition(encounterId: EncounterId? = null): List<Condition> {
    val conditionList =
      this.map { diagnose ->
        val oid =
          "urn:oid:" +
            when (diagnose.system) {
              DiagnoseSystem.ICPC2 -> no.nav.tsm.diagnoser.ICPC2.OID
              DiagnoseSystem.ICD10 -> no.nav.tsm.diagnoser.ICD10.OID
            }
        Condition(
          id = diagnose.id.value.toString(),
          subject =
            Reference(
              reference =
                com.google.fhir.model.r4.String(value = "Patient/${diagnose.patientId.value}")
            ),
          encounter =
            encounterId?.value?.let {
              Reference(reference = com.google.fhir.model.r4.String(value = "Encounter/$it"))
            },
          code =
            CodeableConcept(
              coding =
                listOf(
                  Coding(
                    system = Uri(value = oid),
                    code = Code(value = diagnose.kode),
                    display = com.google.fhir.model.r4.String(value = diagnose.beskrivelse),
                  )
                )
            ),
        )
      }
    return conditionList
  }
}
