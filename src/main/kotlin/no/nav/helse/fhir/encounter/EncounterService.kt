package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.fhir.patient.PatientInputId

class EncounterService(private val epjClient: HttpClient) {

  suspend fun getEncounterById(encounterId: EncounterId): Encounter? {
    val response = epjClient.get("/api/konsultasjon/${encounterId.value}")
    if (response.status != HttpStatusCode.OK) return null // TODO tidy
    return response.body<Konsultasjon>().toEncounter()
  }

  suspend fun getActiveEncounterByPatient(patientId: PatientInputId): Encounter? {
    val response = epjClient.get("/api/patients/${patientId.value}/konsultasjoner/active")
    if (response.status != HttpStatusCode.OK) return null // TODO tidy
    return response.body<Konsultasjon>().toEncounter()
  }

  fun Konsultasjon.toEncounter(): Encounter {
    val status =
      when (this.status) {
        KonsultasjonStatus.PLANLAGT -> Encounter.EncounterStatus.Planned
        KonsultasjonStatus.PÅGÅENDE -> Encounter.EncounterStatus.In_Progress
        KonsultasjonStatus.FULLFØRT -> Encounter.EncounterStatus.Finished
        KonsultasjonStatus.AVLYST -> Encounter.EncounterStatus.Cancelled
      }
    return Encounter(
      id = this.id.value.toString(),
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/${this.pasientId}")),
      participant =
        this.hpr.map {
          Encounter.Participant(
            individual =
              Reference(reference = com.google.fhir.model.r4.String(value = "Practitioner/$hpr"))
          )
        },
      diagnosis =
        this.diagnoser.map {
          Encounter.Diagnosis(
            condition =
              Reference(reference = com.google.fhir.model.r4.String(value = "Condition/${it.kode}"))
          )
        },
      serviceProvider =
        Reference(reference = com.google.fhir.model.r4.String(value = "Organization/Hardkodet")),
      status = Enumeration(value = status),
      type =
        listOf(
          CodeableConcept(
            coding =
              listOf(
                Coding(system = Uri("urn:oid:2.16.578.1.12.4.1.1.8432"), code = Code("kontakttype"))
              )
          )
        ),
      `class` =
        Coding(
          code = Code(value = "VR"),
          system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        ),
    )
  }
}
