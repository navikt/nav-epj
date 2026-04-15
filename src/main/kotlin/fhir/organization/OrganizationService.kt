package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Organization
import no.nav.helse.fhir.organization.repository.OrganizationRepository

class OrganizationService(private val repository: OrganizationRepository) {

  fun getOrganization(id: String): Organization? {
    return repository.getOrganization(id)
  }

  fun getAllOrganizations(): List<Organization> {
    return repository.getAllOrganizations()
  }

  fun createOrganization(organization: Organization): Organization {
    return repository.createOrganization(organization)
  }
}
