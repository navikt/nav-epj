package no.nav.helse.fhir.Condition

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.epj.konsultasjon.Diagnose
import no.nav.helse.fhir.Encounter.EncounterId
import no.nav.helse.fhir.Patient.PatientInputId

@OptIn(ExperimentalUuidApi::class)
class ConditionService(private val epjClient: HttpClient) {

  suspend fun getConditions(encounterId: EncounterId, patientId: PatientInputId): List<Condition> {
    val diagnoser = epjClient.get("/api/diagnose/${encounterId.value}").body<List<Diagnose>>()

    return diagnoser.toCondition(encounterId, patientId)
  }

  private fun List<Diagnose>.toCondition(
    encounterId: EncounterId,
    patientId: PatientInputId,
  ): List<Condition> {
    return this.map {
      Condition(
        id = encounterId.value.toString(),
        subject =
          Reference(reference = com.google.fhir.model.r4.String(value = "Patient/${patientId}")),
        code =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "OID"), // TODO
                  code = Code(value = it.kode),
                  display = com.google.fhir.model.r4.String(value = it.beskrivelse),
                )
              )
          ),
      )
    }
  }
}
