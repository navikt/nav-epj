package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.QuestionnaireResponse

class QuestionnaireResponseService(private val repository: QuestionnaireResponseRepository) {

  suspend fun getQuestionnaireResponse(id: String): QuestionnaireResponse? {
    return repository.getById(id)
  }

  suspend fun getAllQuestionnaireResponses(): List<QuestionnaireResponse> {
    return repository.getAll()
  }

  suspend fun createQuestionnaireResponse(
    questionnaireResponse: QuestionnaireResponse
  ): QuestionnaireResponse {
    return repository.create(questionnaireResponse)
  }

  suspend fun upsertQuestionnaireResponse(
    questionnaireResponse: QuestionnaireResponse
  ): UpsertResult<QuestionnaireResponse> {
    return repository.upsert(questionnaireResponse)
  }
}
