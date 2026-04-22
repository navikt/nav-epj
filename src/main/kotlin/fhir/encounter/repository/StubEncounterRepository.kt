package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.Code
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
      status = Enumeration(value = EncounterStatus.Finished),
      `class` = Coding(
        system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        code = Code(value = "AMB"),
        display = com.google.fhir.model.r4.String(value = "ambulatory")
      ),
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-001"),
        display = com.google.fhir.model.r4.String(value = "Li Jun")
      ),
      participant = listOf(
        Encounter.Participant(
          individual = Reference(
            reference = com.google.fhir.model.r4.String(value = "Practitioner/practitioner-001"),
            display = com.google.fhir.model.r4.String(value = "Dr. Carl Boom")
          )
        )
      ),
      period = Period(
        start = DateTime(value = FhirDateTime.fromString("2024-01-15T09:00:00Z")),
        end = DateTime(value = FhirDateTime.fromString("2024-01-15T09:30:00Z"))
      )
    ),

    Encounter(
      id = "encounter-002",
      status = Enumeration(value = EncounterStatus.In_Progress),
      `class` = Coding(
        system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        code = Code(value = "IMP"),
        display = com.google.fhir.model.r4.String(value = "inpatient encounter")
      ),
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-002"),
        display = com.google.fhir.model.r4.String(value = "Elle McGibbons")
      ),
      participant = listOf(
        Encounter.Participant(
          individual = Reference(
            reference = com.google.fhir.model.r4.String(value = "Practitioner/practitioner-002"),
            display = com.google.fhir.model.r4.String(value = "Dr. Zev Mudskipper")
          )
        )
      ),
      period = Period(
        start = DateTime(value = FhirDateTime.fromString("2024-03-10T14:00:00Z"))
      )
    ),

    Encounter(
      id = "encounter-003",
      status = Enumeration(value = EncounterStatus.Planned),
      `class` = Coding(
        system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        code = Code(value = "AMB"),
        display = com.google.fhir.model.r4.String(value = "ambulatory")
      ),
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-003"),
        display = com.google.fhir.model.r4.String(value = "Jack Wee")
      ),
      participant = listOf(
        Encounter.Participant(
          individual = Reference(
            reference = com.google.fhir.model.r4.String(value = "Practitioner/practitioner-001"),
            display = com.google.fhir.model.r4.String(value = "Dr. Carl Boom")
          )
        )
      ),
      period = Period(
        start = DateTime(value = FhirDateTime.fromString("2024-04-20T10:00:00Z"))
      )
    )
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
