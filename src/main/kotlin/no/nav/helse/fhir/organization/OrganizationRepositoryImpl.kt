package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Organization
import java.util.UUID
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class OrganizationRepositoryImpl : OrganizationRepository {

  override suspend fun getById(id: String): Organization? {
    return dbQuery {
      OrganizationTable.selectAll()
        .where { OrganizationTable.id eq id }
        .singleOrNull()
        ?.let { it[OrganizationTable.data] }
    }
  }

  override suspend fun getAll(): List<Organization> {
    return dbQuery { OrganizationTable.selectAll().map { it[OrganizationTable.data] }.toList() }
  }

  override suspend fun create(organization: Organization): Organization {
    val id = organization.id ?: "organization-${UUID.randomUUID()}"
    val organizationData = if (organization.id == null) organization.copy(id = id) else organization

    dbQuery {
      OrganizationTable.insert {
        it[OrganizationTable.id] = id
        it[OrganizationTable.data] = organizationData
      }
    }

    return organizationData
  }
}
