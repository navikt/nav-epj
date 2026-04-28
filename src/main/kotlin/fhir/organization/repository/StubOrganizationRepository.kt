package no.nav.helse.fhir.organization.repository

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.String as FhirString

class StubOrganizationRepository : OrganizationRepository {

  private val organizations = mutableListOf(
    Organization(
      id = "organization-001",
      meta = Meta(
        profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Organization"))
      ),
      identifier = listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
          value = FhirString(value = "organisasjonsnummer / ENH")
        ),
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.2"),
          value = FhirString(value = "her-id")
        )
      ),

      telecom = listOf(
        ContactPoint(
          system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
          value = FhirString(value = "+47 12345678")
        )
      ),
    ),
  )

  override fun getOrganization(id: kotlin.String): Organization? {
    return organizations.find { it.id == id }
  }

  override fun getAllOrganizations(): List<Organization> {
    return organizations
  }

  override fun createOrganization(organization: Organization): Organization {
    val newOrganization = if (organization.id == null) {
      organization.copy(id = "organization-${java.util.UUID.randomUUID()}")
    } else {
      organization
    }
    organizations.add(newOrganization)
    return newOrganization
  }
}
