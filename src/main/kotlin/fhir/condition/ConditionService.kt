package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Condition
import no.nav.helse.fhir.condition.repository.ConditionRepository

class ConditionService(private val repository: ConditionRepository) {

  fun getCondition(id: String): Condition? {
    return repository.getCondition(id)
  }

  fun getAllConditions(): List<Condition> {
    return repository.getAllConditions()
  }

  fun getConditionsForPatient(patientId: String): List<Condition> {
    return repository.getConditionsForPatient(patientId)
  }

  fun getConditionsForEncounter(encounterId: String): List<Condition> {
    return repository.getConditionsForEncounter(encounterId)
  }
}
