package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.Encounter
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

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
