package no.nav.helse.fhir

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.String as FhirString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.condition.repository.ConditionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionServiceTest {

  private val conditionRepository = mockk<ConditionRepository>()

  private val condition1Id = "condition-001"
  private val condition1 = Condition(
    id = condition1Id,
    clinicalStatus = CodeableConcept(
      coding = listOf(
        Coding(
          system = Uri(value = "http://terminology.hl7.org/CodeSystem/condition-clinical"),
          code = Code(value = "active"),
          display = FhirString(value = "Active")
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
      display = FhirString(value = "Ambulatory encounter")
    ),
    recordedDate = DateTime(value = FhirDateTime.fromString("2024-01-15T09:15:00Z"))
  )

  private val condition2 = Condition(
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
      display = FhirString(value = "Inpatient encounter")
    ),
    recordedDate = DateTime(value = FhirDateTime.fromString("2024-03-10T14:30:00Z"))
  )

  private val condition3 = Condition(
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
      display = FhirString(value = "Planned ambulatory encounter")
    ),
    recordedDate = DateTime(value = FhirDateTime.fromString("2024-04-20T10:00:00Z"))
  )

  @Test
  fun `get condition successfully and assert results`() {
    val conditionService = ConditionService(conditionRepository)
    every { conditionRepository.getCondition(any()) } returns condition1
    val condition = conditionService.getCondition(condition1Id)

    assertEquals(condition1.id, condition?.id)
    assertEquals(condition1.clinicalStatus, condition?.clinicalStatus)
    assertEquals(condition1.code, condition?.code)
    assertEquals(condition1.subject, condition?.subject)
    assertEquals(condition1.encounter, condition?.encounter)
  }

  @Test
  fun `get condition with non existing id should return null`() {
    val conditionService = ConditionService(conditionRepository)
    every { conditionRepository.getCondition(any()) } returns null
    val condition = conditionService.getCondition("non-existing-id")

    assertNull(condition)
  }

  @Test
  fun `get all conditions should return all conditions`() {
    val conditionService = ConditionService(conditionRepository)
    every { conditionRepository.getAllConditions() } returns listOf(
      condition1,
      condition2,
      condition3
    )
    val conditions = conditionService.getAllConditions()

    assertEquals(3, conditions.size)
    assertTrue { conditions[0].id == condition1.id }
  }

  @Test
  fun `get conditions returns an empty list when there are no conditions`() {
    val conditionService = ConditionService(conditionRepository)
    every { conditionRepository.getAllConditions() } returns emptyList()
    val conditions = conditionService.getAllConditions()

    assertTrue { conditions.isEmpty() }
  }

  @Test
  fun `get conditions for patient returns matching conditions`() {
    val conditionService = ConditionService(conditionRepository)
    every { conditionRepository.getConditionsForPatient("patient-001") } returns listOf(condition1)
    val conditions = conditionService.getConditionsForPatient("patient-001")

    assertEquals(1, conditions.size)
    assertEquals(condition1.id, conditions[0].id)
  }

  @Test
  fun `get conditions for encounter returns matching conditions`() {
    val conditionService = ConditionService(conditionRepository)
    every { conditionRepository.getConditionsForEncounter("encounter-001") } returns listOf(condition1)
    val conditions = conditionService.getConditionsForEncounter("encounter-001")

    assertEquals(1, conditions.size)
    assertEquals(condition1.id, conditions[0].id)
  }

}
