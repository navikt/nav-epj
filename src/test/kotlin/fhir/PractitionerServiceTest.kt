package no.nav.helse.fhir

import com.google.fhir.model.r4.Boolean
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitioner.repository.PractitionerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PractitionerServiceTest {

  val practitionerRepository = mockk<PractitionerRepository>()

  val erikPractitionerId = "practitioner-001"
  val erikThePractitioner = Practitioner(
    id = erikPractitionerId,
    active = Boolean(value = true),
    name = listOf(
      HumanName(
        family = String(value = "Boom"),
        given = listOf(String(value = "Carl")),
        prefix = listOf(String(value = "Dr."))
      ),
    ),
    gender = Enumeration(value = AdministrativeGender.Male),
    birthDate = Date(value = FhirDate.fromString("1975-06-20")),
  )

  val mariaPractitioner = Practitioner(
    id = "practitioner-002",
    active = Boolean(value = true),
    name = listOf(
      HumanName(
        family = String(value = "Mudskipper"),
        given = listOf(String(value = "Zev")),
        prefix = listOf(String(value = "Dr."))
      ),
    ),
    gender = Enumeration(value = AdministrativeGender.Female),
    birthDate = Date(value = FhirDate.fromString("1982-09-14")),
  )

  val andersPractitioner = Practitioner(
    id = "practitioner-003",
    active = Boolean(value = false),
    name = listOf(
      HumanName(
        family = String(value = "Andrews"),
        given = listOf(String(value = "Chris")),
        prefix = listOf(String(value = "Dr."))
      ),
    ),
    gender = Enumeration(value = AdministrativeGender.Male),
    birthDate = Date(value = FhirDate.fromString("1968-02-28")),
  )

  @Test
  fun `get practitioner successfully and assert results`() {
    val practitionerService = PractitionerService(practitionerRepository)
    every { practitionerRepository.getPractitioner(any()) } returns erikThePractitioner
    val practitioner = practitionerService.getPractitioner(erikPractitionerId)

    assertEquals(erikThePractitioner.id, practitioner?.id)
    assertEquals(erikThePractitioner.active, practitioner?.active)
    assertEquals(erikThePractitioner.name, practitioner?.name)
    assertEquals(erikThePractitioner.gender, practitioner?.gender)
    assertEquals(erikThePractitioner.birthDate, practitioner?.birthDate)
  }

  @Test
  fun `get practitioner with non existing id should return null`() {
    val practitionerService = PractitionerService(practitionerRepository)
    every { practitionerRepository.getPractitioner(any()) } returns null
    val practitioner = practitionerService.getPractitioner("non-existing-id")

    assertEquals(null, practitioner)
  }

  @Test
  fun `get all practitioners should return all practitioners and assert that there are three practitioners`() {
    val practitionerService = PractitionerService(practitionerRepository)
    every { practitionerRepository.getAllPractitioners() } returns listOf(erikThePractitioner, mariaPractitioner, andersPractitioner)
    val practitioners = practitionerService.getAllPractitioners()

    assertEquals(3, practitioners.size)
    assertTrue { practitioners[0].id == erikThePractitioner.id }
  }

  @Test
  fun `get practitioners returns an empty list when there are no practitioners`() {
    val practitionerService = PractitionerService(practitionerRepository)
    every { practitionerRepository.getAllPractitioners() } returns emptyList()
    val practitioners = practitionerService.getAllPractitioners()
    assertTrue { practitioners.isEmpty() }
  }
}
