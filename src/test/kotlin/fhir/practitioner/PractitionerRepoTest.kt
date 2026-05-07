package fhir.practitioner

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.core.db.DatabaseConnection
import no.nav.helse.core.db.dbQuery
import no.nav.helse.fhir.practitioner.repository.PractitionerRepo
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Integration tests for PractitionerRepo. Uses testcontainers with PostgreSQL to test actual
 * database operations.
 */
class PractitionerRepoTest {

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

      // Initialize database connection
      DatabaseConnection.database =
        R2dbcDatabase.Companion.connect(
          url =
            "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
          user = postgres.username,
          password = postgres.password,
        )
    }
  }

  private lateinit var repo: PractitionerRepo

  @BeforeTest
  fun setup() {
    repo = PractitionerRepo()

    // Clean up table before each test
    runBlocking { dbQuery { exec("DELETE FROM practitioner") } }
  }

  private fun createTestPractitioner(
    id: String = "practitioner-001",
    familyName: String = "Boom",
    givenName: String = "Carl",
    gender: AdministrativeGender = AdministrativeGender.Male,
    active: Boolean = true,
  ) =
    Practitioner(
      id = id,
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
            )
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
            value = com.google.fhir.model.r4.String(value = "9144889"),
          )
        ),
      active = com.google.fhir.model.r4.Boolean(value = active),
      name =
        listOf(
          HumanName(
            family = com.google.fhir.model.r4.String(value = familyName),
            given = listOf(com.google.fhir.model.r4.String(value = givenName)),
            prefix = listOf(com.google.fhir.model.r4.String(value = "Dr.")),
          )
        ),
      gender = Enumeration(value = gender),
      birthDate = Date(value = FhirDate.Companion.fromString("1975-06-20")),
    )

  @Test
  fun `create practitioner persists to database`() = runBlocking {
    val practitioner = createTestPractitioner()

    val created = repo.createPractitioner(practitioner)

    assertEquals(practitioner.id, created.id)
    assertEquals(practitioner.name, created.name)
  }

  @Test
  fun `get practitioner returns persisted practitioner`() = runBlocking {
    val practitioner = createTestPractitioner()
    repo.createPractitioner(practitioner)

    val retrieved = repo.getPractitioner(practitioner.id!!)

    assertNotNull(retrieved)
    assertEquals(practitioner.id, retrieved.id)
    assertEquals(practitioner.meta, retrieved.meta)
    assertEquals(practitioner.identifier, retrieved.identifier)
    assertEquals(practitioner.active?.value, retrieved.active?.value)
    assertEquals(practitioner.name, retrieved.name)
    assertEquals(practitioner.gender?.value, retrieved.gender?.value)
    assertEquals(practitioner.birthDate, retrieved.birthDate)
  }

  @Test
  fun `get practitioner with non-existing id returns null`() = runBlocking {
    val retrieved = repo.getPractitioner("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `get all practitioners returns all persisted practitioners`() = runBlocking {
    val practitioner1 = createTestPractitioner(id = "practitioner-001", familyName = "Boom")
    val practitioner2 = createTestPractitioner(id = "practitioner-002", familyName = "Smith")
    val practitioner3 = createTestPractitioner(id = "practitioner-003", familyName = "Jones")

    repo.createPractitioner(practitioner1)
    repo.createPractitioner(practitioner2)
    repo.createPractitioner(practitioner3)

    val all = repo.getAllPractitioners()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "practitioner-001" })
    assertTrue(all.any { it.id == "practitioner-002" })
    assertTrue(all.any { it.id == "practitioner-003" })
  }

  @Test
  fun `get all practitioners returns empty list when no practitioners exist`() = runBlocking {
    val all = repo.getAllPractitioners()

    assertTrue(all.isEmpty())
  }

  @Test
  fun `create practitioner without id generates uuid`() = runBlocking {
    val practitioner =
      Practitioner(
        id = null,
        meta =
          Meta(
            profile =
              listOf(
                Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
              )
          ),
        identifier =
          listOf(
            Identifier(
              system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
              value = com.google.fhir.model.r4.String(value = "9144889"),
            )
          ),
        active = com.google.fhir.model.r4.Boolean(value = true),
        name =
          listOf(
            HumanName(
              family = com.google.fhir.model.r4.String(value = "Boom"),
              given = listOf(com.google.fhir.model.r4.String(value = "Carl")),
              prefix = listOf(com.google.fhir.model.r4.String(value = "Dr.")),
            )
          ),
        gender = Enumeration(value = AdministrativeGender.Male),
        birthDate = Date(value = FhirDate.Companion.fromString("1975-06-20")),
      )

    val created = repo.createPractitioner(practitioner)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("practitioner-"))
  }

  @Test
  fun `practitioner with female gender is stored and retrieved correctly`() = runBlocking {
    val practitioner =
      createTestPractitioner(
        id = "practitioner-female",
        familyName = "Smith",
        givenName = "Jane",
        gender = AdministrativeGender.Female,
      )

    repo.createPractitioner(practitioner)
    val retrieved = repo.getPractitioner("practitioner-female")

    assertNotNull(retrieved)
    assertEquals(AdministrativeGender.Female, retrieved.gender?.value)
  }

  @Test
  fun `inactive practitioner is stored and retrieved correctly`() = runBlocking {
    val practitioner = createTestPractitioner(id = "practitioner-inactive", active = false)

    repo.createPractitioner(practitioner)
    val retrieved = repo.getPractitioner("practitioner-inactive")

    assertNotNull(retrieved)
    assertEquals(false, retrieved.active?.value)
  }
}
