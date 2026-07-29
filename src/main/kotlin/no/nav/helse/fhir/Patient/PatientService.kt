package no.nav.helse.fhir.Patient

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.epj.pasient.Pasient

@OptIn(ExperimentalUuidApi::class)
class PatientService(private val epjClient: HttpClient) {

  suspend fun getPatient(patientInputId: PatientInputId): Patient? {
    val patient = epjClient.get("/api/patient/${patientInputId.value}").body<Pasient>()

    return patient.toPatient()
  }

  fun Pasient.toPatient(): Patient {
    return Patient(
      meta =
        Meta(
          profile =
            listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
        ),
      id = this.id.value.toString(),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
            value = com.google.fhir.model.r4.String(value = this.fnr),
          )
        ),
      name =
        listOf(
          HumanName(
            family = com.google.fhir.model.r4.String(value = this.navn),
            given = listOf(com.google.fhir.model.r4.String(value = this.navn)),
          )
        ),
    )
  }
}
