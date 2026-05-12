package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Practitioner
import java.util.UUID
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class PractitionerRepositoryImpl : PractitionerRepository {

  override suspend fun getById(id: String): Practitioner? {
    return dbQuery {
      PractitionerTable.selectAll()
        .where { PractitionerTable.id eq id }
        .singleOrNull()
        ?.let { it[PractitionerTable.data] }
    }
  }

  override suspend fun getAll(): List<Practitioner> {
    return dbQuery { PractitionerTable.selectAll().map { it[PractitionerTable.data] }.toList() }
  }

  override suspend fun create(practitioner: Practitioner): Practitioner {
    val id = practitioner.id ?: "practitioner-${UUID.randomUUID()}"
    val practitionerData = if (practitioner.id == null) practitioner.copy(id = id) else practitioner

    dbQuery {
      PractitionerTable.insert {
        it[PractitionerTable.id] = id
        it[PractitionerTable.data] = practitionerData
      }
    }

    return practitionerData
  }
}
