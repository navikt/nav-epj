package no.nav.helse.fhir.DocumentReference

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
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
/*          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

      try {
        val encounterId = DocumentReferenceId(Uuid.parse(id))
        val bundle = documentReferenceService.getDocumentReference(encounterId)
        call.respondText(fhirjson.encodeToString(bundle), fhirContentType)
      } catch (e: Exception) {
        log.error("Encounter returned error while fetching Encounters", e)
      }*/
    }
  }
}
