package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.QuestionnaireResponse
import java.util.UUID
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

data class UpsertResult<T>(val resource: T, val created: Boolean)

class QuestionnaireResponseRepositoryImpl : QuestionnaireResponseRepository {

  override suspend fun getById(id: String): QuestionnaireResponse? {
    return dbQuery {
      QuestionnaireResponseTable.selectAll()
        .where { QuestionnaireResponseTable.id eq id }
        .singleOrNull()
        ?.let { it[QuestionnaireResponseTable.data] }
    }
  }

  override suspend fun getAll(): List<QuestionnaireResponse> {
    return dbQuery {
      QuestionnaireResponseTable.selectAll().map { it[QuestionnaireResponseTable.data] }.toList()
    }
  }

  override suspend fun create(questionnaireResponse: QuestionnaireResponse): QuestionnaireResponse {
    val id = questionnaireResponse.id ?: "questionnaire-response-${UUID.randomUUID()}"
    val data =
      if (questionnaireResponse.id == null) questionnaireResponse.copy(id = id)
      else questionnaireResponse

    dbQuery {
      QuestionnaireResponseTable.insert {
        it[QuestionnaireResponseTable.id] = id
        it[QuestionnaireResponseTable.data] = data
      }
    }

    return data
  }

  override suspend fun upsert(
    questionnaireResponse: QuestionnaireResponse
  ): UpsertResult<QuestionnaireResponse> {
    val id =
      questionnaireResponse.id
        ?: throw IllegalArgumentException("QuestionnaireResponse must have an id for upsert")

    val created = dbQuery {
      val exists =
        QuestionnaireResponseTable.selectAll()
          .where { QuestionnaireResponseTable.id eq id }
          .count() > 0

      if (exists) {
        QuestionnaireResponseTable.update({ QuestionnaireResponseTable.id eq id }) {
          it[QuestionnaireResponseTable.data] = questionnaireResponse
        }
        false
      } else {
        QuestionnaireResponseTable.insert {
          it[QuestionnaireResponseTable.id] = id
          it[QuestionnaireResponseTable.data] = questionnaireResponse
        }
        true
      }
    }

    return UpsertResult(questionnaireResponse, created)
  }
}
