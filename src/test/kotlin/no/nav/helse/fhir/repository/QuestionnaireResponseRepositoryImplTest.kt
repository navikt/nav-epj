package no.nav.helse.fhir.repository

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.Reference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.core.db.DatabaseConnection
import no.nav.helse.core.db.dbQuery
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseRepositoryImpl
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.testcontainers.containers.PostgreSQLContainer

class QuestionnaireResponseRepositoryImplTest {

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
        Database.Companion.connect(
          url =
            "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
          driver = "org.postgresql.Driver",
          user = postgres.username,
          password = postgres.password,
        )
    }
  }

  private lateinit var repo: QuestionnaireResponseRepositoryImpl

  @BeforeTest
  fun setup() {
    repo = QuestionnaireResponseRepositoryImpl()

    runBlocking { dbQuery { QuestionnaireResponseTable.deleteAll() } }
  }

  private fun createTestQuestionnaireResponse(
    id: String = "qr-001",
    questionnaire: String =
      "https://www.nav.no/samarbeidspartner/sykmelding/fhir/R4/Questionnaire/V1",
    status: QuestionnaireResponse.QuestionnaireResponseStatus =
      QuestionnaireResponse.QuestionnaireResponseStatus.Completed,
  ) =
    QuestionnaireResponse(
      id = id,
      questionnaire = Canonical(value = questionnaire),
      status = Enumeration(value = status),
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-001")),
      encounter =
        Reference(reference = com.google.fhir.model.r4.String(value = "Encounter/encounter-001")),
      author =
        Reference(
          reference = com.google.fhir.model.r4.String(value = "Practitioner/practitioner-001")
        ),
    )

  @Test
  fun `should persist questionnaire response to database`() = runBlocking {
    val qr = createTestQuestionnaireResponse()

    val created = repo.create(qr)

    assertEquals(qr.id, created.id)
    assertEquals(qr.questionnaire, created.questionnaire)
  }

  @Test
  fun `should return persisted questionnaire response by id`() = runBlocking {
    val qr = createTestQuestionnaireResponse()
    repo.create(qr)

    val retrieved = repo.getById(qr.id!!)

    assertNotNull(retrieved)
    assertEquals(qr.id, retrieved.id)
    assertEquals(qr.questionnaire, retrieved.questionnaire)
    assertEquals(qr.status.value, retrieved.status.value)
    assertEquals(qr.subject, retrieved.subject)
    assertEquals(qr.encounter, retrieved.encounter)
    assertEquals(qr.author, retrieved.author)
  }

  @Test
  fun `should return null when questionnaire response id does not exist`() = runBlocking {
    val retrieved = repo.getById("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `should return all persisted questionnaire responses`() = runBlocking {
    val qr1 = createTestQuestionnaireResponse(id = "qr-001")
    val qr2 = createTestQuestionnaireResponse(id = "qr-002")
    val qr3 = createTestQuestionnaireResponse(id = "qr-003")

    repo.create(qr1)
    repo.create(qr2)
    repo.create(qr3)

    val all = repo.getAll()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "qr-001" })
    assertTrue(all.any { it.id == "qr-002" })
    assertTrue(all.any { it.id == "qr-003" })
  }

  @Test
  fun `should return empty list when no questionnaire responses exist`() = runBlocking {
    val all = repo.getAll()

    assertTrue(all.isEmpty())
  }

  @Test
  fun `should generate uuid when questionnaire response has no id`() = runBlocking {
    val qr =
      QuestionnaireResponse(
        id = null,
        questionnaire =
          Canonical(
            value = "https://www.nav.no/samarbeidspartner/sykmelding/fhir/R4/Questionnaire/V1"
          ),
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
      )

    val created = repo.create(qr)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("questionnaire-response-"))
  }

  @Test
  fun `should upsert questionnaire response creating new entry`() = runBlocking {
    val qr = createTestQuestionnaireResponse(id = "qr-upsert-new")

    val result = repo.upsert(qr)

    assertTrue(result.created)
    assertEquals(qr.id, result.resource.id)

    val retrieved = repo.getById("qr-upsert-new")
    assertNotNull(retrieved)
    assertEquals(qr.questionnaire, retrieved.questionnaire)
  }

  @Test
  fun `should upsert questionnaire response updating existing entry`() = runBlocking {
    val originalQr = createTestQuestionnaireResponse(id = "qr-upsert-update")
    repo.create(originalQr)

    val updatedQr =
      createTestQuestionnaireResponse(
        id = "qr-upsert-update",
        status = QuestionnaireResponse.QuestionnaireResponseStatus.Amended,
      )

    val result = repo.upsert(updatedQr)

    assertFalse(result.created)
    assertEquals(
      QuestionnaireResponse.QuestionnaireResponseStatus.Amended,
      result.resource.status.value,
    )

    val retrieved = repo.getById("qr-upsert-update")
    assertNotNull(retrieved)
    assertEquals(QuestionnaireResponse.QuestionnaireResponseStatus.Amended, retrieved.status.value)
  }

  @Test
  fun `should store and retrieve in-progress questionnaire response`() = runBlocking {
    val qr =
      createTestQuestionnaireResponse(
        id = "qr-in-progress",
        status = QuestionnaireResponse.QuestionnaireResponseStatus.In_Progress,
      )

    repo.create(qr)
    val retrieved = repo.getById("qr-in-progress")

    assertNotNull(retrieved)
    assertEquals(
      QuestionnaireResponse.QuestionnaireResponseStatus.In_Progress,
      retrieved.status.value,
    )
  }
}
