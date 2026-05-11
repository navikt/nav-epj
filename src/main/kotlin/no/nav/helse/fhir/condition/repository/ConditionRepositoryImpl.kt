package no.nav.helse.fhir.condition.repository

import com.google.fhir.model.r4.Condition
import java.util.UUID
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

class ConditionRepositoryImpl : ConditionRepository {

  override suspend fun getById(id: String): Condition? {
    return dbQuery {
      ConditionTable.selectAll()
        .where { ConditionTable.id eq id }
        .singleOrNull()
        ?.let { it[ConditionTable.data] }
    }
  }

  override suspend fun getAll(): List<Condition> {
    return dbQuery { ConditionTable.selectAll().map { it[ConditionTable.data] }.toList() }
  }

  override suspend fun getByPatientId(patientId: String): List<Condition> {
    val patientReference = "Patient/$patientId"
    return dbQuery {
      ConditionTable.selectAll()
        .map { it[ConditionTable.data] }
        .filter { it.subject.reference?.value == patientReference }
        .toList()
    }
  }

  override suspend fun getByEncounterId(encounterId: String): List<Condition> {
    val encounterReference = "Encounter/$encounterId"
    return dbQuery {
      ConditionTable.selectAll()
        .map { it[ConditionTable.data] }
        .filter { it.encounter?.reference?.value == encounterReference }
        .toList()
    }
  }

  override suspend fun create(condition: Condition): Condition {
    val id = condition.id ?: "condition-${UUID.randomUUID()}"
    val conditionData = if (condition.id == null) condition.copy(id = id) else condition

    dbQuery {
      ConditionTable.insert {
        it[ConditionTable.id] = id
        it[ConditionTable.data] = conditionData
      }
    }

    return conditionData
  }
}
