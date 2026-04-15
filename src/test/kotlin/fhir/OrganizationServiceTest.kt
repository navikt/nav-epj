package no.nav.helse.fhir

import com.google.fhir.model.r4.Address
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.Boolean as FhirBoolean
import com.google.fhir.model.r4.String as FhirString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.organization.repository.OrganizationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrganizationServiceTest {

  private val organizationRepository = mockk<OrganizationRepository>()

  private val organization1Id = "organization-001"
  private val organization1 = Organization(
    id = organization1Id,
    active = FhirBoolean(value = true),
    identifier = listOf(
      Identifier(
        system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
        value = FhirString(value = "993467049")
      )
    ),
    type = listOf(
      CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/organization-type"),
            code = Code(value = "prov"),
            display = FhirString(value = "Healthcare Provider")
          )
        )
      )
    ),
    name = FhirString(value = "Oslo universitetssykehus HF"),
    alias = listOf(FhirString(value = "OUS")),
    address = listOf(
      Address(
        city = FhirString(value = "Oslo"),
        country = FhirString(value = "NO")
      )
    )
  )

  private val organization2 = Organization(
    id = "organization-002",
    active = FhirBoolean(value = true),
    name = FhirString(value = "St. Olavs hospital HF"),
    address = listOf(
      Address(
        city = FhirString(value = "Trondheim"),
        country = FhirString(value = "NO")
      )
    )
  )

  private val organization3 = Organization(
    id = "organization-003",
    active = FhirBoolean(value = true),
    name = FhirString(value = "Arbeids- og velferdsetaten"),
    alias = listOf(FhirString(value = "NAV"))
  )

  @Test
  fun `get organization successfully and assert results`() {
    val organizationService = OrganizationService(organizationRepository)
    every { organizationRepository.getOrganization(any()) } returns organization1
    val organization = organizationService.getOrganization(organization1Id)

    assertEquals(organization1.id, organization?.id)
    assertEquals(organization1.name, organization?.name)
    assertEquals(organization1.active, organization?.active)
    assertEquals(organization1.type, organization?.type)
    assertEquals(organization1.address, organization?.address)
  }

  @Test
  fun `get organization with non existing id should return null`() {
    val organizationService = OrganizationService(organizationRepository)
    every { organizationRepository.getOrganization(any()) } returns null
    val organization = organizationService.getOrganization("non-existing-id")

    assertNull(organization)
  }

  @Test
  fun `get all organizations should return all organizations`() {
    val organizationService = OrganizationService(organizationRepository)
    every { organizationRepository.getAllOrganizations() } returns listOf(
      organization1,
      organization2,
      organization3
    )
    val organizations = organizationService.getAllOrganizations()

    assertEquals(3, organizations.size)
    assertTrue { organizations[0].id == organization1.id }
  }

  @Test
  fun `get organizations returns an empty list when there are no organizations`() {
    val organizationService = OrganizationService(organizationRepository)
    every { organizationRepository.getAllOrganizations() } returns emptyList()
    val organizations = organizationService.getAllOrganizations()

    assertTrue { organizations.isEmpty() }
  }

  @Test
  fun `create organization successfully`() {
    val organizationService = OrganizationService(organizationRepository)
    val newOrganization = Organization(
      id = "organization-new",
      active = FhirBoolean(value = true),
      name = FhirString(value = "Helse Nord RHF"),
      type = listOf(
        CodeableConcept(
          coding = listOf(
            Coding(
              system = Uri(value = "http://terminology.hl7.org/CodeSystem/organization-type"),
              code = Code(value = "prov"),
              display = FhirString(value = "Healthcare Provider")
            )
          )
        )
      )
    )
    every { organizationRepository.createOrganization(any()) } returns newOrganization

    val created = organizationService.createOrganization(newOrganization)
    verify(exactly = 1) { organizationRepository.createOrganization(newOrganization) }

    assertEquals(newOrganization.id, created.id)
    assertEquals(newOrganization.name, created.name)
    assertEquals(newOrganization.active, created.active)
    assertEquals(newOrganization.type, created.type)
  }
}
