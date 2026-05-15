package no.nav.helse.fhir.service

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.Reference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseRepository
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseService
import no.nav.helse.fhir.questionnaireresponse.UpsertResult

class QuestionnaireResponseServiceTest {

  private val repository = mockk<QuestionnaireResponseRepository>()

  private fun createTestQuestionnaireResponse(
    id: String = "qr-001",
    status: QuestionnaireResponse.QuestionnaireResponseStatus =
      QuestionnaireResponse.QuestionnaireResponseStatus.Completed,
  ) =
    QuestionnaireResponse(
      id = id,
      questionnaire =
        Canonical(
          value = "https://www.nav.no/samarbeidspartner/sykmelding/fhir/R4/Questionnaire/V1"
        ),
      status = Enumeration(value = status),
      subject =
        Reference(reference = com.google.fhir.model.r4.String(value = "Patient/patient-001")),
      encounter =
        Reference(reference = com.google.fhir.model.r4.String(value = "Encounter/encounter-001")),
    )

  @Test
  fun `should return questionnaire response when id exists`() = runBlocking {
    val service = QuestionnaireResponseService(repository)
    val qr = createTestQuestionnaireResponse()
    coEvery { repository.getById(any()) } returns qr

    val result = service.getQuestionnaireResponse("qr-001")

    coVerify(exactly = 1) { repository.getById("qr-001") }
    assertEquals(qr.id, result?.id)
    assertEquals(qr.questionnaire, result?.questionnaire)
    assertEquals(qr.status, result?.status)
  }

  @Test
  fun `should return null when questionnaire response id does not exist`() = runBlocking {
    val service = QuestionnaireResponseService(repository)
    coEvery { repository.getById(any()) } returns null

    val result = service.getQuestionnaireResponse("non-existing")

    coVerify(exactly = 1) { repository.getById("non-existing") }
    assertNull(result)
  }

  @Test
  fun `should return all questionnaire responses`() = runBlocking {
    val service = QuestionnaireResponseService(repository)
    val qr1 = createTestQuestionnaireResponse(id = "qr-001")
    val qr2 = createTestQuestionnaireResponse(id = "qr-002")
    coEvery { repository.getAll() } returns listOf(qr1, qr2)

    val results = service.getAllQuestionnaireResponses()

    coVerify(exactly = 1) { repository.getAll() }
    assertEquals(2, results.size)
    assertTrue(results.any { it.id == "qr-001" })
    assertTrue(results.any { it.id == "qr-002" })
  }

  @Test
  fun `should return empty list when no questionnaire responses exist`() = runBlocking {
    val service = QuestionnaireResponseService(repository)
    coEvery { repository.getAll() } returns emptyList()

    val results = service.getAllQuestionnaireResponses()

    coVerify(exactly = 1) { repository.getAll() }
    assertTrue(results.isEmpty())
  }

  @Test
  fun `should create questionnaire response successfully`() = runBlocking {
    val service = QuestionnaireResponseService(repository)
    val qr = createTestQuestionnaireResponse()
    coEvery { repository.create(any()) } returns qr

    val created = service.createQuestionnaireResponse(qr)

    coVerify(exactly = 1) { repository.create(qr) }
    assertEquals(qr.id, created.id)
    assertEquals(qr.questionnaire, created.questionnaire)
  }

  @Test
  fun `should upsert questionnaire response successfully`() = runBlocking {
    val service = QuestionnaireResponseService(repository)

    // First upsert - creates the resource
    val qr = createTestQuestionnaireResponse()
    val createResult = UpsertResult(qr, created = true)
    coEvery { repository.upsert(qr) } returns createResult

    val firstResult = service.upsertQuestionnaireResponse(qr)

    coVerify(exactly = 1) { repository.upsert(qr) }
    assertEquals(qr.id, firstResult.resource.id)
    assertTrue(firstResult.created)

    // Second upsert - updates the existing resource with new status
    val updatedQr =
      createTestQuestionnaireResponse(
        id = "qr-001",
        status = QuestionnaireResponse.QuestionnaireResponseStatus.Amended,
      )
    val updateResult = UpsertResult(updatedQr, created = false)
    coEvery { repository.upsert(updatedQr) } returns updateResult

    val secondResult = service.upsertQuestionnaireResponse(updatedQr)

    coVerify(exactly = 1) { repository.upsert(updatedQr) }
    assertEquals(updatedQr.id, secondResult.resource.id)
    assertEquals(
      QuestionnaireResponse.QuestionnaireResponseStatus.Amended,
      secondResult.resource.status.value,
    )
    assertTrue(!secondResult.created, "Second upsert should be an update, not a create")
  }
}
