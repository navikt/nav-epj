package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.QuestionnaireResponse
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object QuestionnaireResponseTable : Table("questionnaire_response") {
  val id = text("id")
  val data = fhirResource<QuestionnaireResponse>("data")
}
