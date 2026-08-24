package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.documentReferenceId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

fun Route.documentReferenceRoutes(
  documentReferenceService: DocumentReferenceService,
  fhirjson: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/DocumentReference/{documentreferenceId}") {
      val id = call.documentReferenceId()
      val principal = call.requireFhirScope("DocumentReference", Interaction.READ)

      val documentReference =
        documentReferenceService.getDocumentReferences(id)
          ?: return@get call.respond(HttpStatusCode.NotFound, "No documentReference found for $id")
      principal.requirePatientMatch(
        "DocumentReference",
        Interaction.READ,
        documentReference.subject?.reference?.value?.substringAfter("Patient/"),
      )

      call.respondText(fhirjson.encodeToString(documentReference), fhirContentType)
    }
    post("/DocumentReference") {
      val body = call.receiveText()
      val documentReference = fhirjson.decodeFromString(body) as DocumentReference
      val principal = call.requireFhirScope("DocumentReference", Interaction.CREATE)
      principal.requirePatientMatch(
        "DocumentReference",
        Interaction.CREATE,
        documentReference.subject?.reference?.value?.substringAfter("Patient/"),
      )

      val created = documentReferenceService.createDocumentReference(documentReference)
      if (created) {
        call.respond(HttpStatusCode.OK)
      } else {
        call.respond(HttpStatusCode.Conflict)
      }
    }
  }
}
