package no.nav.helse.fhir.condition.repository

import com.google.fhir.model.r4.Condition

interface ConditionRepository {
  fun getCondition(id: String): Condition?

  fun getAllConditions(): List<Condition>

  fun getConditionsForPatient(patientId: String): List<Condition>

  fun getConditionsForEncounter(encounterId: String): List<Condition>

  fun createCondition(condition: Condition): Condition
}
