package no.nav.helse.ehr.consultation.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.ehr.clinicalNoteId
import no.nav.helse.ehr.consultation.ClinicalNote
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.ehr.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.clinicalNoteRoutes(
    consultationService: ConsultationService,
    sessionOrgResolver: SessionOrgResolver,
) {

    val log = logger()

    route("/api") {
        route("/clinical-notes") {
            get("/{clinicalNoteId}") {
                val id = call.clinicalNoteId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val clinicalNote =
                    consultationService.getClinicalNote(id, orgNumber)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                log.info("journalnotat: $clinicalNote")
                call.respond(clinicalNote)
            }
            // The document a SMART app attached to the note, such as a PDF written back on submit
            get("/{clinicalNoteId}/attachment") {
                val id = call.clinicalNoteId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val attachment =
                    consultationService.getClinicalNoteAttachment(id, orgNumber)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(
                            ContentDisposition.Parameters.FileName,
                            attachment.title,
                        )
                        .toString(),
                )
                call.respondBytes(attachment.data, ContentType.Application.Pdf)
            }
            post {
                val request = call.receive<ClinicalNote>()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                if (consultationService.insertClinicalNote(request, orgNumber)) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Kunne ikke opprette journalnotat",
                    )
                }
            }
        }
    }
}
