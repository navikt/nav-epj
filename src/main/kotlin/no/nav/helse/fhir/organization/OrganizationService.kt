package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Organization
import no.nav.helse.fhir.organization.repository.OrganizationRepository

class OrganizationService(private val repository: OrganizationRepository) {

  suspend fun getOrganization(id: String): Organization? {
    return repository.getById(id)
  }

  suspend fun getAllOrganizations(): List<Organization> {
    return repository.getAll()
  }

  suspend fun createOrganization(organization: Organization): Organization {
    return repository.create(organization)
  }
}
