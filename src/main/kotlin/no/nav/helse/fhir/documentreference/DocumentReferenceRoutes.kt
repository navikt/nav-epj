package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.patient.PatientInputId

fun Route.documentReferenceRoutes(
  documentReferenceService: DocumentReferenceService,
  fhirjson: FhirR4Json,
  fhirContentType: ContentType,
) {
  val log = logger()
  route("/fhir") {
    get("/DocumentReference") {
      val patientId =
        call.parameters["patient"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing patient")
      val type =
        call.parameters["type"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing type")

      if (type != "urn:oid:2.16.578.1.12.4.1.1.9602|J01-2") {
        return@get call.respond(
          HttpStatusCode.BadRequest,
          "Missing type",
        ) // TODO: usikker på om vi trenger denne sjekken
      }

      try {
        val patientInputId = PatientInputId(Uuid.parse(patientId))
        val documentReferences =
          documentReferenceService.getDocumentReferences(patientInputId)
            ?: return@get call.respond(
              HttpStatusCode.NotFound,
              "No documentReference found for $patientInputId",
            )

        val bundle =
          Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
              documentReferences.map { document ->
                Bundle.Entry(
                  fullUrl = Uri(value = "DocumentReference/${document.id}"),
                  resource = document,
                )
              },
          )

        call.respondText(fhirjson.encodeToString(bundle), fhirContentType)
      } catch (e: Exception) {
        log.error("Error while fetching DocumentReference", e)
        call.respond(HttpStatusCode.BadRequest, "DocumentReference returned error")
      }
    }
    put("/DocumentReference/{id}") {
      val body = call.receiveText()

      try {
        val documentReference = fhirjson.decodeFromString(body) as DocumentReference
        val created = documentReferenceService.createDocumentReference(documentReference)
        if (created) {
          call.respond(documentReference)
        } else {
          call.respond(HttpStatusCode.Conflict)
        }
      } catch (e: Exception) {
        log.error("Error while creating DocumentReference", e)
      }
    }
  }
}
