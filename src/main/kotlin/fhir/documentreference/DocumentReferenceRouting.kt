package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.documentreference.repository.StubDocumentReferenceRepository
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.respondFhir

fun Route.configureDocumentReferenceRouting() {
  val documentReferenceService = DocumentReferenceService(StubDocumentReferenceRepository())
  get("/DocumentReference/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val id = call.parameters["id"]
      ?: return@get call.respondText("Missing document reference id", status = HttpStatusCode.BadRequest)
    val documentReference = documentReferenceService.getDocumentReference(id)
    if (documentReference != null) {
      call.respondFhir(documentReference)
    } else {
      call.respondText("DocumentReference not found", status = HttpStatusCode.NotFound)
    }
  }

  get("/DocumentReference") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val documentReferences = documentReferenceService.getAllDocumentReferences()
    val bundle = Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      entry = documentReferences.map { documentReference ->
        Bundle.Entry(
          fullUrl = Uri(value = "DocumentReference/${documentReference.id}"),
          resource = documentReference
        )
      }
    )
    call.respondFhir(bundle)
  }

  post("/DocumentReference") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@post
    }
    val body = call.receiveText()
    val documentReference = fhirJson.decodeFromString(body) as DocumentReference
    val created = documentReferenceService.createDocumentReference(documentReference)
    call.respondFhir(created)
  }
}
