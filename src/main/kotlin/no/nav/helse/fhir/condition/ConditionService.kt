package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Condition
import no.nav.helse.fhir.condition.repository.ConditionRepository

class ConditionService(private val repository: ConditionRepository) {

  suspend fun getCondition(id: String): Condition? {
    return repository.getById(id)
  }

  suspend fun getAllConditions(): List<Condition> {
    return repository.getAll()
  }

  suspend fun getConditionsForPatient(patientId: String): List<Condition> {
    return repository.getByPatientId(patientId)
  }

  suspend fun getConditionsForEncounter(encounterId: String): List<Condition> {
    return repository.getByEncounterId(encounterId)
  }

  suspend fun createCondition(condition: Condition): Condition {
    return repository.create(condition)
  }
}
