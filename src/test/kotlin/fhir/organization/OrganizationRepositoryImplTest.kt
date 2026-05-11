package fhir.organization

import com.google.fhir.model.r4.Boolean as FhirBoolean
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
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
import no.nav.helse.fhir.organization.OrganizationRepositoryImpl
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.testcontainers.containers.PostgreSQLContainer

class OrganizationRepositoryImplTest {

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

  private lateinit var repo: OrganizationRepositoryImpl

  @BeforeTest
  fun setup() {
    repo = OrganizationRepositoryImpl()

    runBlocking { dbQuery { exec("DELETE FROM organization") } }
  }

  private fun createTestOrganization(
    id: String = "organization-001",
    name: String = "Test Organization",
    active: Boolean = true,
  ) =
    Organization(
      id = id,
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Organization")
            )
        ),
      active = FhirBoolean(value = active),
      name = FhirString(value = name),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
            value = FhirString(value = "123456789"),
          )
        ),
      telecom =
        listOf(
          ContactPoint(
            system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
            value = FhirString(value = "+47 12345678"),
          )
        ),
    )

  @Test
  fun `create organization persists to database`() = runBlocking {
    val organization = createTestOrganization()

    val created = repo.create(organization)

    assertEquals(organization.id, created.id)
    assertEquals(organization.name, created.name)
  }

  @Test
  fun `get organization returns persisted organization`() = runBlocking {
    val organization = createTestOrganization()
    repo.create(organization)

    val retrieved = repo.getById(organization.id!!)

    assertNotNull(retrieved)
    assertEquals(organization.id, retrieved.id)
    assertEquals(organization.meta, retrieved.meta)
    assertEquals(organization.name, retrieved.name)
    assertEquals(organization.active?.value, retrieved.active?.value)
    assertEquals(organization.identifier, retrieved.identifier)
    assertEquals(organization.telecom, retrieved.telecom)
  }

  @Test
  fun `get organization with non-existing id returns null`() = runBlocking {
    val retrieved = repo.getById("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `get all organizations returns all persisted organizations`() = runBlocking {
    val org1 = createTestOrganization(id = "organization-001", name = "Org One")
    val org2 = createTestOrganization(id = "organization-002", name = "Org Two")
    val org3 = createTestOrganization(id = "organization-003", name = "Org Three")

    repo.create(org1)
    repo.create(org2)
    repo.create(org3)

    val all = repo.getAll()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "organization-001" })
    assertTrue(all.any { it.id == "organization-002" })
    assertTrue(all.any { it.id == "organization-003" })
  }

  @Test
  fun `get all organizations returns empty list when no organizations exist`() = runBlocking {
    val all = repo.getAll()

    assertTrue(all.isEmpty())
  }

  @Test
  fun `create organization without id generates uuid`() = runBlocking {
    val organization =
      Organization(
        id = null,
        name = FhirString(value = "New Organization"),
        active = FhirBoolean(value = true),
      )

    val created = repo.create(organization)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("organization-"))
  }

  @Test
  fun `organization with different active states is stored and retrieved correctly`() =
    runBlocking {
      val activeOrg = createTestOrganization(id = "org-active", active = true)
      val inactiveOrg = createTestOrganization(id = "org-inactive", active = false)

      repo.create(activeOrg)
      repo.create(inactiveOrg)

      val retrievedActive = repo.getById("org-active")
      val retrievedInactive = repo.getById("org-inactive")

      assertNotNull(retrievedActive)
      assertEquals(true, retrievedActive.active?.value)

      assertNotNull(retrievedInactive)
      assertEquals(false, retrievedInactive.active?.value)
    }

  @Test
  fun `organization with type is stored and retrieved correctly`() = runBlocking {
    val organization =
      Organization(
        id = "org-with-type",
        name = FhirString(value = "Healthcare Provider"),
        type =
          listOf(
            CodeableConcept(
              coding =
                listOf(
                  Coding(
                    system = Uri(value = "http://terminology.hl7.org/CodeSystem/organization-type"),
                    code = Code(value = "prov"),
                    display = FhirString(value = "Healthcare Provider"),
                  )
                )
            )
          ),
      )

    repo.create(organization)
    val retrieved = repo.getById("org-with-type")

    assertNotNull(retrieved)
    assertEquals("prov", retrieved.type.first().coding.first().code?.value)
  }
}
