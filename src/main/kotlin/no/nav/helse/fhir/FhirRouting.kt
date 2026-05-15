package no.nav.helse.fhir

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Resource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import no.nav.helse.auth.UserSession
import no.nav.helse.fhir.bundle.TransactionBundleService
import no.nav.helse.fhir.bundle.TransactionResult
import no.nav.helse.fhir.condition.configureConditionRouting
import no.nav.helse.fhir.documentreference.configureDocumentReferenceRouting
import no.nav.helse.fhir.encounter.configureEncounterRouting
import no.nav.helse.fhir.organization.configureOrganizationRouting
import no.nav.helse.fhir.patient.configurePatientRouting
import no.nav.helse.fhir.practitioner.configurePractitionerRouting
import no.nav.helse.fhir.questionnaireresponse.configureQuestionnaireResponseRouting

val fhirJson = FhirR4Json()
const val FHIR_CONTENT_TYPE = "application/fhir+json"

fun Application.configureFhirRouting() {
  routing {
    route("fhir") {
      configurePatientRouting()
      configureOrganizationRouting()
      configureEncounterRouting()
      configureConditionRouting()
      configurePractitionerRouting()
      configureDocumentReferenceRouting()
      configureQuestionnaireResponseRouting()
      configureTransactionBundleRouting()
    }
  }
}

fun Route.configureTransactionBundleRouting() {
  val transactionService: TransactionBundleService by application.dependencies

  put("/") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@put
    }

    val body = call.receiveText()
    val bundle = fhirJson.decodeFromString(body) as? Bundle
    if (bundle == null) {
      call.respondText("Request body must be a Bundle", status = HttpStatusCode.BadRequest)
      return@put
    }

    when (val result = transactionService.processTransaction(bundle)) {
      is TransactionResult.Success -> {
        call.respondFhir(result.responseBundle)
      }
      is TransactionResult.Error -> {
        call.respondText(result.message, status = HttpStatusCode.fromValue(result.statusCode))
      }
    }
  }
}

fun ApplicationCall.isAuthenticated(): Boolean {
  val session = sessions.get<UserSession>()
  return session?.accessToken != null
}

suspend inline fun <reified T : Resource> ApplicationCall.respondFhir(resource: T) {
  respondText(fhirJson.encodeToString(resource), ContentType.parse(FHIR_CONTENT_TYPE))
}
