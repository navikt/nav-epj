package no.nav.helse.fhir.organization.repository

import com.google.fhir.model.r4.Organization

interface OrganizationRepository {
    fun getOrganization(id: String): Organization?

    fun getAllOrganizations(): List<Organization>

    fun createOrganization(organization: Organization): Organization
}
