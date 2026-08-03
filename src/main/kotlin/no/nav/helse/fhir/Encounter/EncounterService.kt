package no.nav.helse.fhir.Encounter

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.collections.map
import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.fhir.Patient.PatientInputId

@OptIn(ExperimentalUuidApi::class)
class EncounterService(private val epjClient: HttpClient) {

  suspend fun getEncounterById(encounterId: EncounterId): Encounter {
    val konsultasjon = epjClient.get("/api/konsultasjon/${encounterId.value}").body<Konsultasjon>()

    return konsultasjon.toEncounter()
  }

  suspend fun getEncounterByPatientId(patientId: PatientInputId): Encounter {
    val konsultasjon =
      epjClient.get("/api/patient/${patientId.value}/konsultasjon").body<Konsultasjon>()

    return konsultasjon.toEncounter()
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
