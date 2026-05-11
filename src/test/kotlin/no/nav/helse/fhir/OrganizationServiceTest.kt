package no.nav.helse.fhir

import com.google.fhir.model.r4.Boolean as FhirBoolean
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.organization.repository.OrganizationRepository

class OrganizationServiceTest {

  private val organizationRepository = mockk<OrganizationRepository>()

  private val organization1Id = "organization-001"
  private val organization1 =
    Organization(
      id = "organization-001",
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Organization")
            )
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
            value = FhirString(value = "organisasjonsnummer / ENH"),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.2"),
            value = FhirString(value = "her-id"),
          ),
        ),
      telecom =
        listOf(
          ContactPoint(
            system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
            value = FhirString(value = "+47 12345678"),
          )
        ),
    )

  @Test
  fun `get organization successfully and assert results`() = runBlocking {
    val organizationService = OrganizationService(organizationRepository)
    coEvery { organizationRepository.getById(any()) } returns organization1
    val organization = organizationService.getOrganization(organization1Id)

    assertEquals(organization1.id, organization?.id)
    assertEquals(organization1.name, organization?.name)
    assertEquals(organization1.active, organization?.active)
    assertEquals(organization1.type, organization?.type)
    assertEquals(organization1.address, organization?.address)
  }

  @Test
  fun `get organization with non existing id should return null`() = runBlocking {
    val organizationService = OrganizationService(organizationRepository)
    coEvery { organizationRepository.getById(any()) } returns null
    val organization = organizationService.getOrganization("non-existing-id")

    assertNull(organization)
  }

  @Test
  fun `get all organizations should return all organizations`() = runBlocking {
    val organizationService = OrganizationService(organizationRepository)
    coEvery { organizationRepository.getAll() } returns listOf(organization1)
    val organizations = organizationService.getAllOrganizations()

    assertEquals(1, organizations.size)
    assertTrue { organizations[0].id == organization1.id }
  }

  @Test
  fun `get organizations returns an empty list when there are no organizations`() = runBlocking {
    val organizationService = OrganizationService(organizationRepository)
    coEvery { organizationRepository.getAll() } returns emptyList()
    val organizations = organizationService.getAllOrganizations()

    assertTrue { organizations.isEmpty() }
  }

  @Test
  fun `create organization successfully`() = runBlocking {
    val organizationService = OrganizationService(organizationRepository)
    val newOrganization =
      Organization(
        id = "organization-new",
        active = FhirBoolean(value = true),
        name = FhirString(value = "Helse Nord RHF"),
        type =
          listOf(
            CodeableConcept(
              coding =
                listOf(
                  Coding(
                    system = Uri(value = "http://terminology.hl7.org/CodeSystem/organization-type"),
                    code = Code(value = "prov"),
                    display = FhirString(value = "Healthcare Provider"),
                  )
                )
            )
          ),
      )
    coEvery { organizationRepository.create(any()) } returns newOrganization

    val created = organizationService.createOrganization(newOrganization)
    coVerify(exactly = 1) { organizationRepository.create(newOrganization) }

    assertEquals(newOrganization.id, created.id)
    assertEquals(newOrganization.name, created.name)
    assertEquals(newOrganization.active, created.active)
    assertEquals(newOrganization.type, created.type)
  }
}
