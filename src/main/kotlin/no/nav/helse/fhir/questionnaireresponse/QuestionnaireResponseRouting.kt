package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.respondFhir

fun Route.configureQuestionnaireResponseRouting() {
  val service: QuestionnaireResponseService by application.dependencies

  get("/QuestionnaireResponse/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val id =
      call.parameters["id"]
        ?: return@get call.respondText(
          "Missing QuestionnaireResponse id",
          status = HttpStatusCode.BadRequest,
        )
    val questionnaireResponse = service.getQuestionnaireResponse(id)
    if (questionnaireResponse != null) {
      call.respondFhir(questionnaireResponse)
    } else {
      call.respondText("QuestionnaireResponse not found", status = HttpStatusCode.NotFound)
    }
  }

  get("/QuestionnaireResponse") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val responses = service.getAllQuestionnaireResponses()
    val bundle =
      Bundle(
        type = Enumeration(value = Bundle.BundleType.Searchset),
        entry =
          responses.map { qr ->
            Bundle.Entry(fullUrl = Uri(value = "QuestionnaireResponse/${qr.id}"), resource = qr)
          },
      )
    call.respondFhir(bundle)
  }

  post("/QuestionnaireResponse") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@post
    }
    val body = call.receiveText()
    val questionnaireResponse = fhirJson.decodeFromString(body) as QuestionnaireResponse
    val created = service.createQuestionnaireResponse(questionnaireResponse)
    call.response.status(HttpStatusCode.Created)
    call.respondFhir(created)
  }

  put("/QuestionnaireResponse/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@put
    }
    val id =
      call.parameters["id"]
        ?: return@put call.respondText(
          "Missing QuestionnaireResponse id",
          status = HttpStatusCode.BadRequest,
        )
    val body = call.receiveText()
    val questionnaireResponse = fhirJson.decodeFromString(body) as QuestionnaireResponse

    if (questionnaireResponse.id != id) {
      return@put call.respondText(
        "Resource id does not match URL id",
        status = HttpStatusCode.BadRequest,
      )
    }

    val result = service.upsertQuestionnaireResponse(questionnaireResponse)
    val statusCode = if (result.created) HttpStatusCode.Created else HttpStatusCode.OK
    call.response.status(statusCode)
    call.respondFhir(result.resource)
  }
}
