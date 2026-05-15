package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.QuestionnaireResponse

interface QuestionnaireResponseRepository {
  suspend fun getById(id: String): QuestionnaireResponse?

  suspend fun getAll(): List<QuestionnaireResponse>

  suspend fun create(questionnaireResponse: QuestionnaireResponse): QuestionnaireResponse

  suspend fun upsert(
    questionnaireResponse: QuestionnaireResponse
  ): UpsertResult<QuestionnaireResponse>
}
