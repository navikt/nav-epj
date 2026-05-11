package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Practitioner
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

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
