package no.nav.helse.fhir

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.UnsignedInt
import com.google.fhir.model.r4.Uri
import no.nav.helse.epj.EpjService
import no.nav.helse.epj.api.Konsultasjon

class FhirService(private val epjService: EpjService) {
  private fun Konsultasjon.toEncounter(): Encounter {
    val status =
      when (this.status) {
        "pågående" -> Encounter.EncounterStatus.In_Progress
        "fullført" -> Encounter.EncounterStatus.Finished
        else -> Encounter.EncounterStatus.In_Progress
      }

    return Encounter(
      id = this.id,
      status = Enumeration(value = status),
      `class` =
        Coding(
          code = Code(value = "AMB"),
          system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        ),
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/${this.pasientId}")),
    )
  }

  suspend fun getPatient(id: String): Patient? {
    val pasient = epjService.getPasient(id) ?: return null
    return Patient(
      id = pasient.id,
      name = listOf(HumanName(text = com.google.fhir.model.r4.String(value = pasient.navn))),
    )
  }

  suspend fun getEncounter(id: String, authorizedPatientId: String): Encounter? {
    val konsultasjon = epjService.getKonsultasjon(id) ?: return null
    if (konsultasjon.pasientId != authorizedPatientId) return null
    return konsultasjon.toEncounter()
  }

  suspend fun getActiveEncounterForPatient(patientId: String): Encounter? =
    epjService.getAktivKonsultasjon(patientId)?.toEncounter()

  suspend fun searchEncounters(patientId: String): Bundle {
    val encounters = epjService.getKonsultasjoner(patientId).map { it.toEncounter() }
    return Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      total = UnsignedInt(value = encounters.size),
      entry = encounters.map { Bundle.Entry(resource = it) },
    )
  }
}
