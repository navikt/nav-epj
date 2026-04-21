package no.nav.helse.fhir

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Period
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.encounter.repository.EncounterRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncounterServiceTest {

  val encounterRepository = mockk<EncounterRepository>()

  val encounter1Id = "encounter-001"
  val encounter1 = Encounter(
    id = encounter1Id,
    status = Enumeration(value = Encounter.EncounterStatus.Finished),
    `class` = Coding(
      system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
      code = Code(value = "AMB"),
      display = String(value = "ambulatory"),
    ),
    subject = Reference(
      reference = String(value = "Patient/patient-001"),
      display = String(value = "Li Jun"),
    ),
    period = Period(
      start = DateTime(value = FhirDateTime.fromString("2024-01-15T09:00:00Z")),
      end = DateTime(value = FhirDateTime.fromString("2024-01-15T09:30:00Z")),
    ),
  )

  val encounter2 = Encounter(
    id = "encounter-002",
    status = Enumeration(value = Encounter.EncounterStatus.In_Progress),
    `class` = Coding(
      system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
      code = Code(value = "IMP"),
      display = String(value = "inpatient encounter"),
    ),
    subject = Reference(
      reference = String(value = "Patient/patient-002"),
      display = String(value = "Elle McGibbons"),
    ),
    period = Period(
      start = DateTime(value = FhirDateTime.fromString("2024-03-10T14:00:00Z")),
    ),
  )

  val encounter3 = Encounter(
    id = "encounter-003",
    status = Enumeration(value = Encounter.EncounterStatus.Planned),
    `class` = Coding(
      system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
      code = Code(value = "AMB"),
      display = String(value = "ambulatory"),
    ),
    subject = Reference(
      reference = String(value = "Patient/patient-003"),
      display = String(value = "Jack Wee"),
    ),
    period = Period(
      start = DateTime(value = FhirDateTime.fromString("2024-04-20T10:00:00Z")),
    ),
  )

  @Test
  fun `get encounter successfully and assert results`() {
    val encounterService = EncounterService(encounterRepository)
    every { encounterRepository.getEncounter(any()) } returns encounter1
    val encounter = encounterService.getEncounter(encounter1Id)

    assertEquals(encounter1.id, encounter?.id)
    assertEquals(encounter1.status, encounter?.status)
    assertEquals(encounter1.`class`, encounter?.`class`)
    assertEquals(encounter1.subject, encounter?.subject)
    assertEquals(encounter1.period, encounter?.period)
  }

  @Test
  fun `get encounter with non existing id should return null`() {
    val encounterService = EncounterService(encounterRepository)
    every { encounterRepository.getEncounter(any()) } returns null
    val encounter = encounterService.getEncounter("non-existing-id")

    assertEquals(null, encounter)
  }

  @Test
  fun `get all encounters should return all encounters and assert that there are three encounters`() {
    val encounterService = EncounterService(encounterRepository)
    every { encounterRepository.getAllEncounters() } returns listOf(
      encounter1,
      encounter2,
      encounter3,
    )
    val encounters = encounterService.getAllEncounters()

    assertEquals(3, encounters.size)
    assertTrue { encounters[0].id == encounter1.id }
  }

  @Test
  fun `get encounters returns an empty list when there are no encounters`() {
    val encounterService = EncounterService(encounterRepository)
    every { encounterRepository.getAllEncounters() } returns emptyList()
    val encounters = encounterService.getAllEncounters()

    assertTrue { encounters.isEmpty() }
  }

  @Test
  fun `create encounter successfully`() {
    val encounterService = EncounterService(encounterRepository)
    val newEncounter = Encounter(
      id = "encounter-new",
      status = Enumeration(value = Encounter.EncounterStatus.Planned),
      `class` = Coding(
        system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
        code = Code(value = "AMB"),
        display = String(value = "ambulatory"),
      ),
      subject = Reference(
        reference = String(value = "Patient/patient-001"),
        display = String(value = "Li Jun"),
      ),
    )
    every { encounterRepository.createEncounter(any()) } returns newEncounter

    val created = encounterService.createEncounter(newEncounter)
    verify(exactly = 1) { encounterRepository.createEncounter(newEncounter) }

    assertEquals(newEncounter.id, created.id)
    assertEquals(newEncounter.status, created.status)
    assertEquals(newEncounter.`class`, created.`class`)
    assertEquals(newEncounter.subject, created.subject)
  }
}
