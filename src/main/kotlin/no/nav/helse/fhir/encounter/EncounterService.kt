package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Bundle
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

  suspend fun getEncountersByPatient(patientId: PatientInputId): Bundle {
    val response = epjClient.get("/api/patients/${patientId.value}/konsultasjoner")
    if (response.status != HttpStatusCode.OK) {
      return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
    }

    val konsultasjoner = response.body<List<Konsultasjon>>()
    return Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      entry =
        konsultasjoner.map { konsultasjon ->
          val encounter = konsultasjon.toEncounter()
          Bundle.Entry(fullUrl = Uri(value = "Encounter/${encounter.id}"), resource = encounter)
        },
    )
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
        Reference(
          reference = com.google.fhir.model.r4.String(value = "Patient/${this.pasientId.value}")
        ),
      participant =
        this.hpr.map {
          Encounter.Participant(
            individual =
              Reference(reference = com.google.fhir.model.r4.String(value = "Practitioner/$it"))
          )
        },
      reasonCode =
        this.diagnoser.map { diagnose ->
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7170"),
                  code = Code(value = diagnose.kode),
                  display = com.google.fhir.model.r4.String(value = diagnose.beskrivelse),
                )
              )
          )
        },
      diagnosis =
        this.diagnoser.map {
          Encounter.Diagnosis(
            condition =
              Reference(
                reference = com.google.fhir.model.r4.String(value = "Condition/${it.id.value}")
              )
          )
        },
      serviceProvider =
        Reference(
          reference =
            com.google.fhir.model.r4.String(
              value = "Organization/a1000000-0000-0000-0000-000000000001" // TODO hent
            )
        ),
      status = Enumeration(value = status),
      `class` =
        Coding(
          code = Code(value = "VR"),
          system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        ),
    )
  }
}
