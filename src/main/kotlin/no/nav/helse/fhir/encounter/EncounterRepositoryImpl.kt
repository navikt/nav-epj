package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Encounter
import java.util.UUID
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class EncounterRepositoryImpl : EncounterRepository {

  override suspend fun getById(id: String): Encounter? {
    return dbQuery {
      EncounterTable.selectAll()
        .where { EncounterTable.id eq id }
        .singleOrNull()
        ?.let { it[EncounterTable.data] }
    }
  }

  override suspend fun getAll(): List<Encounter> {
    return dbQuery { EncounterTable.selectAll().map { it[EncounterTable.data] }.toList() }
  }

  override suspend fun create(encounter: Encounter): Encounter {
    val id = encounter.id ?: "encounter-${UUID.randomUUID()}"
    val encounterData = if (encounter.id == null) encounter.copy(id = id) else encounter

    dbQuery {
      EncounterTable.insert {
        it[EncounterTable.id] = id
        it[EncounterTable.data] = encounterData
      }
    }

    return encounterData
  }
}
