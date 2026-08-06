package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger

fun Route.documentReferenceRoutes(
  documentReferenceService: DocumentReferenceService,
  fhirjson: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/DocumentReference/{id}") {
      val id =
        call.parameters["id"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing documentreference id")

      try {
        val docrefId = DocumentReferenceId(Uuid.parse(id))
        val documentReference =
          documentReferenceService.getDocumentReferences(docrefId)
            ?: return@get call.respond(
              HttpStatusCode.NotFound,
              "No documentReference found for $docrefId",
            )

        call.respondText(fhirjson.encodeToString(documentReference), fhirContentType)
      } catch (e: Exception) {
        log.error("Error while fetching DocumentReference", e)
        call.respond(HttpStatusCode.BadRequest, "DocumentReference returned error")
      }
    }
    post("/DocumentReference") {
      val body = call.receiveText()

      try {
        val documentReference = fhirjson.decodeFromString(body) as DocumentReference
        val created = documentReferenceService.createDocumentReference(documentReference)
        if (created) {
          call.respond(HttpStatusCode.OK)
        } else {
          call.respond(HttpStatusCode.Conflict)
        }
      } catch (e: Exception) {
        log.error("Error while creating DocumentReference", e)
      }
    }
  }
}
