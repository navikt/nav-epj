package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.documentReferenceId

fun Route.documentReferenceRoutes(
  documentReferenceService: DocumentReferenceService,
  fhirjson: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/DocumentReference/{documentreferenceId}") {
      val id = call.documentReferenceId()
      val documentReference =
        documentReferenceService.getDocumentReferences(id)
          ?: return@get call.respond(HttpStatusCode.NotFound, "No documentReference found for $id")

      call.respondText(fhirjson.encodeToString(documentReference), fhirContentType)
    }
    post("/DocumentReference") {
      val body = call.receiveText()
      val documentReference = fhirjson.decodeFromString(body) as DocumentReference
      val created = documentReferenceService.createDocumentReference(documentReference)
      if (created) {
        call.respond(HttpStatusCode.OK)
      } else {
        call.respond(HttpStatusCode.Conflict)
      }
    }
  }
}
