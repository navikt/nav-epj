package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Condition

interface ConditionRepository {
  suspend fun getById(id: String): Condition?

  suspend fun getAll(): List<Condition>

  suspend fun getByPatientId(patientId: String): List<Condition>

  suspend fun getByEncounterId(encounterId: String): List<Condition>

  suspend fun create(condition: Condition): Condition
}
