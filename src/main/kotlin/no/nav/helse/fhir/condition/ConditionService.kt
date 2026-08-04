package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.nav.helse.epj.konsultasjon.Diagnose
import no.nav.helse.epj.konsultasjon.DiagnoseSystem
import no.nav.helse.fhir.patient.PatientInputId

class ConditionService(private val epjClient: HttpClient) {

  suspend fun getConditions(patientId: PatientInputId): List<Condition> {
    val diagnoser = epjClient.get("/api/diagnoser/${patientId.value}").body<List<Diagnose>>()
    return diagnoser.toCondition(patientId)
  }

  private fun List<Diagnose>.toCondition(patientId: PatientInputId): List<Condition> {
    val conditionList =
      this.map { diagnose ->
        val oid =
          "urn:oid:" +
            when (diagnose.system) {
              DiagnoseSystem.ICPC2 -> no.nav.tsm.diagnoser.ICPC2.OID
              DiagnoseSystem.ICD10 -> no.nav.tsm.diagnoser.ICD10.OID
            }
        Condition(
          id = diagnose.id.toString(),
          subject =
            Reference(reference = com.google.fhir.model.r4.String(value = "Patient/${patientId}")),
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
