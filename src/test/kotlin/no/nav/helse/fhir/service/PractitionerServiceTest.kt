package no.nav.helse.fhir.service

import com.google.fhir.model.r4.Boolean
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.practitioner.PractitionerRepository
import no.nav.helse.fhir.practitioner.PractitionerService

class PractitionerServiceTest {

  val practitionerRepository = mockk<PractitionerRepository>()

  val carlPractitionerId = "practitioner-001"
  val carlThePractitioner =
    Practitioner(
      id = carlPractitionerId,
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
            )
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
            value = String(value = "9144889"),
          )
        ),
      active = Boolean(value = true),
      name =
        listOf(
          HumanName(
            family = String(value = "Boom"),
            given = listOf(String(value = "Carl")),
            prefix = listOf(String(value = "Dr.")),
          )
        ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.Companion.fromString("1975-06-20")),
    )

  val zevPractitioner =
    Practitioner(
      id = "practitioner-002",
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
            )
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
            value = String(value = "9144890"),
          )
        ),
      active = Boolean(value = true),
      name =
        listOf(
          HumanName(
            family = String(value = "Mudskipper"),
            given = listOf(String(value = "Zev")),
            prefix = listOf(String(value = "Dr.")),
          )
        ),
      gender = Enumeration(value = AdministrativeGender.Female),
      birthDate = Date(value = FhirDate.Companion.fromString("1982-09-14")),
    )

  val chrisPractitioner =
    Practitioner(
      id = "practitioner-003",
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
            )
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
            value = String(value = "9144891"),
          )
        ),
      active = Boolean(value = false),
      name =
        listOf(
          HumanName(
            family = String(value = "Andrews"),
            given = listOf(String(value = "Chris")),
            prefix = listOf(String(value = "Dr.")),
          )
        ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.Companion.fromString("1968-02-28")),
    )

  @Test
  fun `get practitioner successfully and assert results`() = runBlocking {
    val practitionerService = PractitionerService(practitionerRepository)
    coEvery { practitionerRepository.getById(any()) } returns carlThePractitioner
    val practitioner = practitionerService.getPractitioner(carlPractitionerId)

    assertEquals(carlThePractitioner.id, practitioner?.id)
    assertEquals(carlThePractitioner.meta, practitioner?.meta)
    assertEquals(carlThePractitioner.identifier, practitioner?.identifier)
    assertEquals(carlThePractitioner.active, practitioner?.active)
    assertEquals(carlThePractitioner.name, practitioner?.name)
    assertEquals(carlThePractitioner.gender, practitioner?.gender)
    assertEquals(carlThePractitioner.birthDate, practitioner?.birthDate)
  }

  @Test
  fun `get practitioner with non existing id should return null`() = runBlocking {
    val practitionerService = PractitionerService(practitionerRepository)
    coEvery { practitionerRepository.getById(any()) } returns null
    val practitioner = practitionerService.getPractitioner("non-existing-id")

    assertEquals(null, practitioner)
  }

  @Test
  fun `get all practitioners should return all practitioners and assert that there are three practitioners`() =
    runBlocking {
      val practitionerService = PractitionerService(practitionerRepository)
      coEvery { practitionerRepository.getAll() } returns
        listOf(carlThePractitioner, zevPractitioner, chrisPractitioner)
      val practitioners = practitionerService.getAllPractitioners()

      assertEquals(3, practitioners.size)
      assertTrue { practitioners[0].id == carlThePractitioner.id }
    }

  @Test
  fun `get practitioners returns an empty list when there are no practitioners`() = runBlocking {
    val practitionerService = PractitionerService(practitionerRepository)
    coEvery { practitionerRepository.getAll() } returns emptyList()
    val practitioners = practitionerService.getAllPractitioners()
    assertTrue { practitioners.isEmpty() }
  }

  @Test
  fun `create practitioner successfully`() = runBlocking {
    val practitionerService = PractitionerService(practitionerRepository)
    val newPractitioner =
      Practitioner(
        id = "practitioner-new",
        meta =
          Meta(
            profile =
              listOf(
                Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
              )
          ),
        identifier =
          listOf(
            Identifier(
              system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
              value = String(value = "9144892"),
            )
          ),
        active = Boolean(value = true),
        name =
          listOf(
            HumanName(
              family = String(value = "Andrews"),
              given = listOf(String(value = "Brandon")),
              prefix = listOf(String(value = "Dr.")),
            )
          ),
        gender = Enumeration(value = AdministrativeGender.Male),
        birthDate = Date(value = FhirDate.Companion.fromString("1980-03-20")),
      )
    coEvery { practitionerRepository.create(any()) } returns newPractitioner

    val created = practitionerService.createPractitioner(newPractitioner)
    coVerify(exactly = 1) { practitionerRepository.create(newPractitioner) }

    assertEquals(newPractitioner.id, created.id)
    assertEquals(newPractitioner.meta, created.meta)
    assertEquals(newPractitioner.identifier, created.identifier)
    assertEquals(newPractitioner.active, created.active)
    assertEquals(newPractitioner.name, created.name)
    assertEquals(newPractitioner.gender, created.gender)
    assertEquals(newPractitioner.birthDate, created.birthDate)
  }
}
