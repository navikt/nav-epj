package no.nav.helse.fhir

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.condition.ConditionRepository

class ConditionServiceTest {

  private val conditionRepository = mockk<ConditionRepository>()

  private val condition1Id = "condition-001"
  private val condition1 =
    Condition(
      id = condition1Id,
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-001")),
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7170"),
                code = Code(value = "L73"),
                display = FhirString(value = "Brudd legg/ankel"),
              )
            )
        ),
    )

  private val condition2 =
    Condition(
      id = "condition-002",
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-002")),
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7170"),
                code = Code(value = "P74"),
                display = FhirString(value = "Angstlidelse"),
              )
            )
        ),
    )

  private val condition3 =
    Condition(
      id = "condition-003",
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-002")),
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7110"),
                code = Code(value = "A051"),
                display = FhirString(value = "Botulisme"),
              )
            )
        ),
    )

  @Test
  fun `get condition successfully and assert results`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    coEvery { conditionRepository.getById(any()) } returns condition1
    val condition = conditionService.getCondition(condition1Id)

    assertEquals(condition1.id, condition?.id)
    assertEquals(condition1.clinicalStatus, condition?.clinicalStatus)
    assertEquals(condition1.code, condition?.code)
    assertEquals(condition1.subject, condition?.subject)
    assertEquals(condition1.encounter, condition?.encounter)
  }

  @Test
  fun `get condition with non existing id should return null`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    coEvery { conditionRepository.getById(any()) } returns null
    val condition = conditionService.getCondition("non-existing-id")

    assertNull(condition)
  }

  @Test
  fun `get all conditions should return all conditions`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    coEvery { conditionRepository.getAll() } returns listOf(condition1, condition2, condition3)
    val conditions = conditionService.getAllConditions()

    assertEquals(3, conditions.size)
    assertTrue { conditions[0].id == condition1.id }
  }

  @Test
  fun `get conditions returns an empty list when there are no conditions`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    coEvery { conditionRepository.getAll() } returns emptyList()
    val conditions = conditionService.getAllConditions()

    assertTrue { conditions.isEmpty() }
  }

  @Test
  fun `get conditions for patient returns matching conditions`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    coEvery { conditionRepository.getByPatientId("patient-001") } returns listOf(condition1)
    val conditions = conditionService.getConditionsForPatient("patient-001")

    assertEquals(1, conditions.size)
    assertEquals(condition1.id, conditions[0].id)
  }

  @Test
  fun `get conditions for encounter returns matching conditions`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    coEvery { conditionRepository.getByEncounterId("encounter-001") } returns listOf(condition1)
    val conditions = conditionService.getConditionsForEncounter("encounter-001")

    assertEquals(1, conditions.size)
    assertEquals(condition1.id, conditions[0].id)
  }

  @Test
  fun `create condition successfully`() = runBlocking {
    val conditionService = ConditionService(conditionRepository)
    val newCondition =
      Condition(
        id = "condition-new",
        subject =
          Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-001")),
        code =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7110"),
                  code = Code(value = "A051"),
                  display = FhirString(value = "Botulisme"),
                )
              )
          ),
      )
    coEvery { conditionRepository.create(any()) } returns newCondition

    val created = conditionService.createCondition(newCondition)
    coVerify(exactly = 1) { conditionRepository.create(newCondition) }

    assertEquals(newCondition.id, created.id)
    assertEquals(newCondition.clinicalStatus, created.clinicalStatus)
    assertEquals(newCondition.code, created.code)
    assertEquals(newCondition.subject, created.subject)
    assertEquals(newCondition.encounter, created.encounter)
  }
}
