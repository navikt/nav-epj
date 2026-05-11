package fhir.condition

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.core.db.DatabaseConnection
import no.nav.helse.core.db.dbQuery
import no.nav.helse.fhir.condition.ConditionRepositoryImpl
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.testcontainers.containers.PostgreSQLContainer

class ConditionRepositoryImplTest {

  companion object {
    private val postgres =
      PostgreSQLContainer("postgres:17").apply {
        withDatabaseName("dr-zara-test")
        withUsername("postgres")
        withPassword("postgres")
        withInitScript("test-init.sql")
      }

    init {
      postgres.start()

      DatabaseConnection.database =
        R2dbcDatabase.Companion.connect(
          url =
            "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
          user = postgres.username,
          password = postgres.password,
        )
    }
  }

  private lateinit var repo: ConditionRepositoryImpl

  @BeforeTest
  fun setup() {
    repo = ConditionRepositoryImpl()

    runBlocking { dbQuery { exec("DELETE FROM condition") } }
  }

  private fun createTestCondition(
    id: String = "condition-001",
    patientId: String = "patient-001",
    code: String = "L73",
    display: String = "Brudd legg/ankel",
    encounterId: String? = null,
  ) =
    Condition(
      id = id,
      subject = Reference(reference = FhirString(value = "Patient/$patientId")),
      encounter = encounterId?.let { Reference(reference = FhirString(value = "Encounter/$it")) },
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.7170"),
                code = Code(value = code),
                display = FhirString(value = display),
              )
            )
        ),
    )

  @Test
  fun `create condition persists to database`() = runBlocking {
    val condition = createTestCondition()

    val created = repo.create(condition)

    assertEquals(condition.id, created.id)
    assertEquals(condition.code, created.code)
  }

  @Test
  fun `get condition returns persisted condition`() = runBlocking {
    val condition = createTestCondition()
    repo.create(condition)

    val retrieved = repo.getById(condition.id!!)

    assertNotNull(retrieved)
    assertEquals(condition.id, retrieved.id)
    assertEquals(condition.code, retrieved.code)
    assertEquals(condition.subject, retrieved.subject)
  }

  @Test
  fun `get condition with non-existing id returns null`() = runBlocking {
    val retrieved = repo.getById("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `get all conditions returns all persisted conditions`() = runBlocking {
    val condition1 = createTestCondition(id = "condition-001", code = "L73")
    val condition2 = createTestCondition(id = "condition-002", code = "P74")
    val condition3 = createTestCondition(id = "condition-003", code = "A051")

    repo.create(condition1)
    repo.create(condition2)
    repo.create(condition3)

    val all = repo.getAll()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "condition-001" })
    assertTrue(all.any { it.id == "condition-002" })
    assertTrue(all.any { it.id == "condition-003" })
  }

  @Test
  fun `get all conditions returns empty list when no conditions exist`() = runBlocking {
    val all = repo.getAll()

    assertTrue(all.isEmpty())
  }

  @Test
  fun `create condition without id generates uuid`() = runBlocking {
    val condition =
      Condition(
        id = null,
        subject = Reference(reference = FhirString(value = "Patient/patient-001")),
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

    val created = repo.create(condition)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("condition-"))
  }

  @Test
  fun `get conditions by patient id returns matching conditions`() = runBlocking {
    val condition1 = createTestCondition(id = "condition-001", patientId = "patient-001")
    val condition2 = createTestCondition(id = "condition-002", patientId = "patient-001")
    val condition3 = createTestCondition(id = "condition-003", patientId = "patient-002")

    repo.create(condition1)
    repo.create(condition2)
    repo.create(condition3)

    val patient1Conditions = repo.getByPatientId("patient-001")

    assertEquals(2, patient1Conditions.size)
    assertTrue(patient1Conditions.all { it.subject.reference?.value == "Patient/patient-001" })
  }

  @Test
  fun `get conditions by patient id returns empty list when no matching conditions`() =
    runBlocking {
      val condition = createTestCondition(id = "condition-001", patientId = "patient-001")
      repo.create(condition)

      val conditions = repo.getByPatientId("patient-999")

      assertTrue(conditions.isEmpty())
    }

  @Test
  fun `get conditions by encounter id returns matching conditions`() = runBlocking {
    val condition1 = createTestCondition(id = "condition-001", encounterId = "encounter-001")
    val condition2 = createTestCondition(id = "condition-002", encounterId = "encounter-001")
    val condition3 = createTestCondition(id = "condition-003", encounterId = "encounter-002")

    repo.create(condition1)
    repo.create(condition2)
    repo.create(condition3)

    val encounter1Conditions = repo.getByEncounterId("encounter-001")

    assertEquals(2, encounter1Conditions.size)
    assertTrue(
      encounter1Conditions.all { it.encounter?.reference?.value == "Encounter/encounter-001" }
    )
  }

  @Test
  fun `get conditions by encounter id returns empty list when no matching conditions`() =
    runBlocking {
      val condition = createTestCondition(id = "condition-001", encounterId = "encounter-001")
      repo.create(condition)

      val conditions = repo.getByEncounterId("encounter-999")

      assertTrue(conditions.isEmpty())
    }

  @Test
  fun `condition with different codes is stored and retrieved correctly`() = runBlocking {
    val condition1 =
      createTestCondition(id = "condition-fracture", code = "L73", display = "Brudd legg/ankel")
    val condition2 =
      createTestCondition(id = "condition-anxiety", code = "P74", display = "Angstlidelse")

    repo.create(condition1)
    repo.create(condition2)

    val retrievedFracture = repo.getById("condition-fracture")
    val retrievedAnxiety = repo.getById("condition-anxiety")

    assertNotNull(retrievedFracture)
    assertEquals("L73", retrievedFracture.code?.coding?.first()?.code?.value)

    assertNotNull(retrievedAnxiety)
    assertEquals("P74", retrievedAnxiety.code?.coding?.first()?.code?.value)
  }
}
