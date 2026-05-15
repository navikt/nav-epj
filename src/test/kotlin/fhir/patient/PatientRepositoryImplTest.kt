package fhir.patient

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Patient
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
import no.nav.helse.fhir.patient.PatientRepositoryImpl
import no.nav.helse.fhir.patient.PatientTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.testcontainers.containers.PostgreSQLContainer

class PatientRepositoryImplTest {

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

  private lateinit var repo: PatientRepositoryImpl

  @BeforeTest
  fun setup() {
    repo = PatientRepositoryImpl()

    runBlocking { dbQuery { PatientTable.deleteAll() } }
  }

  private fun createTestPatient(
    id: String = "patient-001",
    familyName: String = "Li",
    givenName: String = "Jun",
    gender: AdministrativeGender = AdministrativeGender.Male,
    active: Boolean = true,
  ) =
    Patient(
      id = id,
      meta =
        Meta(
          profile =
            listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
            value = com.google.fhir.model.r4.String(value = "12345678901"),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
            value = com.google.fhir.model.r4.String(value = "01234567890"),
          ),
        ),
      active = com.google.fhir.model.r4.Boolean(value = active),
      name =
        listOf(
          HumanName(
            family = com.google.fhir.model.r4.String(value = familyName),
            given = listOf(com.google.fhir.model.r4.String(value = givenName)),
          )
        ),
      gender = Enumeration(value = gender),
      birthDate = Date(value = FhirDate.Companion.fromString("1985-03-15")),
    )

  @Test
  fun `should persist patient to database`() = runBlocking {
    val patient = createTestPatient()

    val created = repo.create(patient)

    assertEquals(patient.id, created.id)
    assertEquals(patient.name, created.name)
  }

  @Test
  fun `should return persisted patient by id`() = runBlocking {
    val patient = createTestPatient()
    repo.create(patient)

    val retrieved = repo.getById(patient.id!!)

    assertNotNull(retrieved)
    assertEquals(patient.id, retrieved.id)
    assertEquals(patient.meta, retrieved.meta)
    assertEquals(patient.identifier, retrieved.identifier)
    assertEquals(patient.active?.value, retrieved.active?.value)
    assertEquals(patient.name, retrieved.name)
    assertEquals(patient.gender?.value, retrieved.gender?.value)
    assertEquals(patient.birthDate, retrieved.birthDate)
  }

  @Test
  fun `should return null when patient id does not exist`() = runBlocking {
    val retrieved = repo.getById("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `should return all persisted patients`() = runBlocking {
    val patient1 = createTestPatient(id = "patient-001", familyName = "Li")
    val patient2 = createTestPatient(id = "patient-002", familyName = "Smith")
    val patient3 = createTestPatient(id = "patient-003", familyName = "Jones")

    repo.create(patient1)
    repo.create(patient2)
    repo.create(patient3)

    val all = repo.getAll()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "patient-001" })
    assertTrue(all.any { it.id == "patient-002" })
    assertTrue(all.any { it.id == "patient-003" })
  }

  @Test
  fun `should return empty list when no patients exist`() = runBlocking {
    val all = repo.getAll()

    assertTrue(all.isEmpty())
  }

  @Test
  fun `should generate uuid when patient has no id`() = runBlocking {
    val patient =
      Patient(
        id = null,
        meta =
          Meta(
            profile =
              listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
          ),
        identifier =
          listOf(
            Identifier(
              system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
              value = com.google.fhir.model.r4.String(value = "12345678901"),
            )
          ),
        active = com.google.fhir.model.r4.Boolean(value = true),
        name =
          listOf(
            HumanName(
              family = com.google.fhir.model.r4.String(value = "Li"),
              given = listOf(com.google.fhir.model.r4.String(value = "Jun")),
            )
          ),
        gender = Enumeration(value = AdministrativeGender.Male),
        birthDate = Date(value = FhirDate.Companion.fromString("1985-03-15")),
      )

    val created = repo.create(patient)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("patient-"))
  }

  @Test
  fun `should store and retrieve female patient correctly`() = runBlocking {
    val patient =
      createTestPatient(
        id = "patient-female",
        familyName = "Smith",
        givenName = "Jane",
        gender = AdministrativeGender.Female,
      )

    repo.create(patient)
    val retrieved = repo.getById("patient-female")

    assertNotNull(retrieved)
    assertEquals(AdministrativeGender.Female, retrieved.gender?.value)
  }

  @Test
  fun `should store and retrieve inactive patient correctly`() = runBlocking {
    val patient = createTestPatient(id = "patient-inactive", active = false)

    repo.create(patient)
    val retrieved = repo.getById("patient-inactive")

    assertNotNull(retrieved)
    assertEquals(false, retrieved.active?.value)
  }
}
