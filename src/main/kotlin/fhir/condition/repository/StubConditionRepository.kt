package no.nav.helse.fhir.condition.repository

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.String as FhirString

class StubConditionRepository : ConditionRepository {

  private val conditions = mutableListOf(
    Condition(
      id = "condition-001",
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-001"),
      ),
      code = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7170"),
            code = Code(value = "L73"),
            display = FhirString(value = "Brudd legg/ankel")
          )
        )
      ),
    ),

    Condition(
      id = "condition-002",
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-002"),
      ),
      code = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7170"),
            code = Code(value = "P74"),
            display = FhirString(value = "Angstlidelse")
          )
        )
      ),
    ),

    Condition(
      id = "condition-003",
      subject = Reference(
        reference = com.google.fhir.model.r4.String(value = "Patient/patient-003"),
      ),
      code = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7110"),
            code = Code(value = "A051"),
            display = FhirString(value = "Botulisme")
          )
        )
      ),
    )
  )

  override fun getCondition(id: kotlin.String): Condition? {
    return conditions.find { it.id == id }
  }

  override fun getAllConditions(): List<Condition> {
    return conditions
  }

  override fun getConditionsForPatient(patientId: kotlin.String): List<Condition> {
    val patientReference = "Patient/$patientId"
    return conditions.filter { it.subject.reference?.value == patientReference }
  }

  override fun getConditionsForEncounter(encounterId: kotlin.String): List<Condition> {
    val encounterReference = "Encounter/$encounterId"
    return conditions.filter { it.encounter?.reference?.value == encounterReference }
  }

  override fun createCondition(condition: Condition): Condition {
    val newCondition = if (condition.id == null) {
      condition.copy(id = "condition-${java.util.UUID.randomUUID()}")
    } else {
      condition
    }
    conditions.add(newCondition)
    return newCondition
  }

}
