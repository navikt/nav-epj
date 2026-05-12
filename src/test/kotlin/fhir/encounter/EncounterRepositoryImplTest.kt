package fhir.encounter

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Encounter.EncounterStatus
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
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
import no.nav.helse.fhir.encounter.EncounterRepositoryImpl
import no.nav.helse.fhir.encounter.EncounterTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.testcontainers.containers.PostgreSQLContainer

class EncounterRepositoryImplTest {

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
        Database.connect(
          url =
            "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
          driver = "org.postgresql.Driver",
          user = postgres.username,
          password = postgres.password,
        )
    }
  }

  private lateinit var repo: EncounterRepositoryImpl

  @BeforeTest
  fun setup() {
    repo = EncounterRepositoryImpl()

    runBlocking { dbQuery { EncounterTable.deleteAll() } }
  }

  private fun createTestEncounter(
    id: String = "encounter-001",
    status: EncounterStatus = EncounterStatus.Finished,
    classCode: String = "AMB",
  ) =
    Encounter(
      id = id,
      status = Enumeration(value = status),
      `class` =
        Coding(
          system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
          code = Code(value = classCode),
        ),
      type =
        listOf(
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.8432"),
                  code = Code("kontaktype"),
                )
              )
          )
        ),
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-001")),
      participant =
        listOf(
          Encounter.Participant(
            individual =
              Reference(
                reference = com.google.fhir.model.r4.String(value = "Practitioner/practitioner-001")
              )
          )
        ),
      diagnosis =
        listOf(
          Encounter.Diagnosis(
            condition =
              Reference(
                reference = com.google.fhir.model.r4.String(value = "Condition/condition-001")
              )
          )
        ),
      serviceProvider =
        Reference(
          reference = com.google.fhir.model.r4.String(value = "Organization/organization-001")
        ),
    )

  @Test
  fun `create encounter persists to database`() = runBlocking {
    val encounter = createTestEncounter()

    val created = repo.create(encounter)

    assertEquals(encounter.id, created.id)
    assertEquals(encounter.status, created.status)
  }

  @Test
  fun `get encounter returns persisted encounter`() = runBlocking {
    val encounter = createTestEncounter()
    repo.create(encounter)

    val retrieved = repo.getById(encounter.id!!)

    assertNotNull(retrieved)
    assertEquals(encounter.id, retrieved.id)
    assertEquals(encounter.status.value, retrieved.status.value)
    assertEquals(encounter.`class`, retrieved.`class`)
    assertEquals(encounter.subject, retrieved.subject)
    assertEquals(encounter.participant, retrieved.participant)
    assertEquals(encounter.diagnosis, retrieved.diagnosis)
    assertEquals(encounter.serviceProvider, retrieved.serviceProvider)
  }

  @Test
  fun `get encounter with non-existing id returns null`() = runBlocking {
    val retrieved = repo.getById("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `get all encounters returns all persisted encounters`() = runBlocking {
    val encounter1 = createTestEncounter(id = "encounter-001")
    val encounter2 = createTestEncounter(id = "encounter-002", status = EncounterStatus.Planned)
    val encounter3 = createTestEncounter(id = "encounter-003", status = EncounterStatus.In_Progress)

    repo.create(encounter1)
    repo.create(encounter2)
    repo.create(encounter3)

    val all = repo.getAll()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "encounter-001" })
    assertTrue(all.any { it.id == "encounter-002" })
    assertTrue(all.any { it.id == "encounter-003" })
  }

  @Test
  fun `get all encounters returns empty list when no encounters exist`() = runBlocking {
    val all = repo.getAll()

    assertTrue(all.isEmpty())
  }

  @Test
  fun `create encounter without id generates uuid`() = runBlocking {
    val encounter =
      Encounter(
        id = null,
        status = Enumeration(value = EncounterStatus.Planned),
        `class` =
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
            code = Code(value = "AMB"),
          ),
        subject =
          Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-001")),
      )

    val created = repo.create(encounter)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("encounter-"))
  }

  @Test
  fun `encounter with different status values is stored and retrieved correctly`() = runBlocking {
    val plannedEncounter =
      createTestEncounter(id = "encounter-planned", status = EncounterStatus.Planned)
    val inProgressEncounter =
      createTestEncounter(id = "encounter-in-progress", status = EncounterStatus.In_Progress)

    repo.create(plannedEncounter)
    repo.create(inProgressEncounter)

    val retrievedPlanned = repo.getById("encounter-planned")
    val retrievedInProgress = repo.getById("encounter-in-progress")

    assertNotNull(retrievedPlanned)
    assertEquals(EncounterStatus.Planned, retrievedPlanned.status.value)

    assertNotNull(retrievedInProgress)
    assertEquals(EncounterStatus.In_Progress, retrievedInProgress.status.value)
  }

  @Test
  fun `encounter with different class codes is stored and retrieved correctly`() = runBlocking {
    val ambulatoryEncounter = createTestEncounter(id = "encounter-amb", classCode = "AMB")
    val inpatientEncounter = createTestEncounter(id = "encounter-imp", classCode = "IMP")

    repo.create(ambulatoryEncounter)
    repo.create(inpatientEncounter)

    val retrievedAmbulatory = repo.getById("encounter-amb")
    val retrievedInpatient = repo.getById("encounter-imp")

    assertNotNull(retrievedAmbulatory)
    assertEquals("AMB", retrievedAmbulatory.`class`.code?.value)

    assertNotNull(retrievedInpatient)
    assertEquals("IMP", retrievedInpatient.`class`.code?.value)
  }
}
