package no.nav.helse.fhir.organization.repository

import com.google.fhir.model.r4.Address
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.Boolean as FhirBoolean
import com.google.fhir.model.r4.String as FhirString

class StubOrganizationRepository : OrganizationRepository {

  private val organizations = mutableListOf(
    // Oslo University Hospital
    Organization(
      id = "organization-001",
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
      telecom = listOf(
        ContactPoint(
          system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
          value = FhirString(value = "+47 12345678")
        )
      ),
      address = listOf(
        Address(
          line = listOf(FhirString(value = "Kirkeveien 166")),
          city = FhirString(value = "Oslo"),
          postalCode = FhirString(value = "0450"),
          country = FhirString(value = "NO")
        )
      )
    ),

    // St. Olavs Hospital
    Organization(
      id = "organization-002",
      active = FhirBoolean(value = true),
      identifier = listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
          value = FhirString(value = "883974832")
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
      name = FhirString(value = "St. Olavs hospital HF"),
      telecom = listOf(
        ContactPoint(
          system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
          value = FhirString(value = "+47 32132122")
        )
      ),
      address = listOf(
        Address(
          line = listOf(FhirString(value = "Prinsesse Kristinas gate 3")),
          city = FhirString(value = "Trondheim"),
          postalCode = FhirString(value = "7030"),
          country = FhirString(value = "NO")
        )
      )
    ),

    // NAV
    Organization(
      id = "organization-003",
      active = FhirBoolean(value = true),
      identifier = listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
          value = FhirString(value = "889640782")
        )
      ),
      type = listOf(
        CodeableConcept(
          coding = listOf(
            Coding(
              system = Uri(value = "http://terminology.hl7.org/CodeSystem/organization-type"),
              code = Code(value = "govt"),
              display = FhirString(value = "Government")
            )
          )
        )
      ),
      name = FhirString(value = "Arbeids- og velferdsetaten"),
      alias = listOf(FhirString(value = "NAV")),
      telecom = listOf(
        ContactPoint(
          system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
          value = FhirString(value = "+47 10098765")
        )
      ),
      address = listOf(
        Address(
          line = listOf(FhirString(value = "Fyrstikkalléen 1")),
          city = FhirString(value = "Oslo"),
          postalCode = FhirString(value = "0661"),
          country = FhirString(value = "NO")
        )
      )
    )
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
