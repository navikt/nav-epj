package no.nav.helse.fhir.service

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Encounter.EncounterStatus
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.encounter.EncounterRepository
import no.nav.helse.fhir.encounter.EncounterService

class EncounterServiceTest {

  val encounterRepository = mockk<EncounterRepository>()

  val encounter1Id = "encounter-001"
  val encounter1 =
    Encounter(
      id = "encounter-001",
      subject = Reference(reference = String(value = "Patient/patient-001")),
      participant =
        listOf(
          Encounter.Participant(
            individual = Reference(reference = String(value = "Practitioner/practitioner-001"))
          )
        ),
      diagnosis =
        listOf(
          Encounter.Diagnosis(
            condition = Reference(reference = String(value = "Condition/condition-001"))
          )
        ),
      serviceProvider = Reference(reference = String(value = "Organization/organization-001")),
      status = Enumeration(value = EncounterStatus.Finished),
      type =
        listOf(
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.8432"),
                  code = Code("kontaktype"),
                )
              )
          )
        ),
      `class` =
        Coding(
          system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
          code = Code(value = "AMB"),
        ),
    )

  @Test
  fun `get encounter successfully and assert results`() = runBlocking {
    val encounterService = EncounterService(encounterRepository)
    coEvery { encounterRepository.getById(any()) } returns encounter1
    val encounter = encounterService.getEncounter(encounter1Id)

    assertEquals(encounter1.id, encounter?.id)
    assertEquals(encounter1.status, encounter?.status)
    assertEquals(encounter1.`class`, encounter?.`class`)
    assertEquals(encounter1.subject, encounter?.subject)
    assertEquals(encounter1.period, encounter?.period)
  }

  @Test
  fun `get encounter with non existing id should return null`() = runBlocking {
    val encounterService = EncounterService(encounterRepository)
    coEvery { encounterRepository.getById(any()) } returns null
    val encounter = encounterService.getEncounter("non-existing-id")

    assertEquals(null, encounter)
  }

  @Test
  fun `get encounters should return and assert that there are one encounter`() = runBlocking {
    val encounterService = EncounterService(encounterRepository)
    coEvery { encounterRepository.getAll() } returns listOf(encounter1)
    val encounters = encounterService.getAllEncounters()

    assertEquals(1, encounters.size)
    assertTrue { encounters[0].id == encounter1.id }
  }

  @Test
  fun `get encounters returns an empty list when there are no encounters`() = runBlocking {
    val encounterService = EncounterService(encounterRepository)
    coEvery { encounterRepository.getAll() } returns emptyList()
    val encounters = encounterService.getAllEncounters()

    assertTrue { encounters.isEmpty() }
  }

  @Test
  fun `create encounter successfully`() = runBlocking {
    val encounterService = EncounterService(encounterRepository)
    val newEncounter =
      Encounter(
        id = "encounter-new",
        status = Enumeration(value = EncounterStatus.Planned),
        `class` =
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
            code = Code(value = "AMB"),
            display = String(value = "ambulatory"),
          ),
        subject =
          Reference(
            reference = String(value = "Patient/patient-001"),
            display = String(value = "Li Jun"),
          ),
      )
    coEvery { encounterRepository.create(any()) } returns newEncounter

    val created = encounterService.createEncounter(newEncounter)
    coVerify(exactly = 1) { encounterRepository.create(newEncounter) }

    assertEquals(newEncounter.id, created.id)
    assertEquals(newEncounter.status, created.status)
    assertEquals(newEncounter.`class`, created.`class`)
    assertEquals(newEncounter.subject, created.subject)
  }
}
