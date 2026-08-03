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
import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.epj.konsultasjon.Diagnose
import no.nav.helse.epj.konsultasjon.DiagnoseSystem
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

@OptIn(ExperimentalUuidApi::class)
class ConditionService(private val epjClient: HttpClient) {

  suspend fun getConditions(encounterId: EncounterId, patientId: PatientInputId): List<Condition> {
    val diagnoser = epjClient.get("/api/diagnoser/${encounterId.value}").body<List<Diagnose>>()
    return diagnoser.toCondition(encounterId, patientId)
  }

  private fun List<Diagnose>.toCondition(
    encounterId: EncounterId,
    patientId: PatientInputId,
  ): List<Condition> {
    val conditionList =
      this.mapIndexed { index, diagnose ->
        val oid =
          "urn:oid:" +
            when (diagnose.system) {
              DiagnoseSystem.ICPC2 -> no.nav.tsm.diagnoser.ICPC2.OID
              DiagnoseSystem.ICD10 -> no.nav.tsm.diagnoser.ICD10.OID
            }
        Condition(
          // Diagnose has no id of its own in the EPJ model; index against the encounter to keep
          // each Condition.id (and therefore Bundle.entry.fullUrl) unique per result.
          id = "${encounterId.value}-$index",
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
