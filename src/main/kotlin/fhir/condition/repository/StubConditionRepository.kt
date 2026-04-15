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
    // Condition for Patient Ola Nordmann from Encounter 001 - Leg/Ankle fracture
    Condition(
      id = "condition-001",
      clinicalStatus = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-clinical"),
            code = Code(value = "active"),
            display = FhirString(value = "Active")
          )
        )
      ),
      verificationStatus = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-ver-status"),
            code = Code(value = "confirmed"),
            display = FhirString(value = "Confirmed")
          )
        )
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
      subject = Reference(
        reference = FhirString(value = "Patient/patient-001"),
        display = FhirString(value = "Ola Nordmann")
      ),
      encounter = Reference(
        reference = FhirString(value = "Encounter/encounter-001"),
        display = FhirString(value = "Ambulatory encounter 2024-01-15")
      ),
      recordedDate = DateTime(value = FhirDateTime.fromString("2024-01-15T09:15:00Z"))
    ),

    // Condition for Patient Kari Nordmann from Encounter 002 - Anxiety disorder
    Condition(
      id = "condition-002",
      clinicalStatus = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-clinical"),
            code = Code(value = "active"),
            display = FhirString(value = "Active")
          )
        )
      ),
      verificationStatus = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-ver-status"),
            code = Code(value = "confirmed"),
            display = FhirString(value = "Confirmed")
          )
        )
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
      subject = Reference(
        reference = FhirString(value = "Patient/patient-002"),
        display = FhirString(value = "Kari Nordmann")
      ),
      encounter = Reference(
        reference = FhirString(value = "Encounter/encounter-002"),
        display = FhirString(value = "Inpatient encounter 2024-03-10")
      ),
      recordedDate = DateTime(value = FhirDateTime.fromString("2024-03-10T14:30:00Z"))
    ),

    // Condition for Patient Per Hansen from Encounter 003 - Botulism
    Condition(
      id = "condition-003",
      clinicalStatus = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-clinical"),
            code = Code(value = "active"),
            display = FhirString(value = "Active")
          )
        )
      ),
      verificationStatus = CodeableConcept(
        coding = listOf(
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-ver-status"),
            code = Code(value = "provisional"),
            display = FhirString(value = "Provisional")
          )
        )
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
      subject = Reference(
        reference = FhirString(value = "Patient/patient-003"),
        display = FhirString(value = "Per Hansen")
      ),
      encounter = Reference(
        reference = FhirString(value = "Encounter/encounter-003"),
        display = FhirString(value = "Planned ambulatory encounter 2024-04-20")
      ),
      recordedDate = DateTime(value = FhirDateTime.fromString("2024-04-20T10:00:00Z"))
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

}
