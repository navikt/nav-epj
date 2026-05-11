package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Organization
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

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
