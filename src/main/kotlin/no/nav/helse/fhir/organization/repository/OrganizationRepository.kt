package no.nav.helse.fhir.organization.repository

import com.google.fhir.model.r4.Organization

interface OrganizationRepository {
  suspend fun getById(id: String): Organization?

  suspend fun getAll(): List<Organization>

  suspend fun create(organization: Organization): Organization
}
