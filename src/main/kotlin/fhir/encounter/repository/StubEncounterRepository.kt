package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Encounter.EncounterStatus
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Period
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri

class StubEncounterRepository : EncounterRepository {

  private val encounters = mutableListOf(
    Encounter(
      id = "encounter-001",
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-001"),
      ),
      participant = listOf(
        Encounter.Participant(
          individual = Reference(
            reference = com.google.fhir.model.r4.String(value = "Practitioner/practitioner-001"),
          )
        )
      ),
      diagnosis = listOf(
        Encounter.Diagnosis(
          condition = Reference(
            reference = com.google.fhir.model.r4.String(value = "Condition/condition-001"),
          )
        )
      ),
      serviceProvider = Reference(
        reference = com.google.fhir.model.r4.String(value = "Organization/organization-001"),
      ),
      status = Enumeration(value = EncounterStatus.Finished),
      type = listOf(
        CodeableConcept(
          coding =listOf(
            Coding(
              system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.8432"),
              code = Code("kontaktype"),
            )
          )
        )
      ),
      `class` = Coding(
        system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        code = Code(value = "AMB"),
      )
    ),
  )

  override fun getEncounter(id: String): Encounter? {
    return encounters.find { it.id == id }
  }

  override fun getAllEncounters(): List<Encounter> {
    return encounters
  }

  override fun createEncounter(encounter: Encounter): Encounter {
    val newEncounter = if (encounter.id == null) {
      encounter.copy(id = "encounter-${java.util.UUID.randomUUID()}")
    } else {
      encounter
    }
    encounters.add(newEncounter)
    return newEncounter
  }
}
