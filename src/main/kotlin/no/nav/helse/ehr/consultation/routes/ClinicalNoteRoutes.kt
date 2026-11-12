package no.nav.helse.ehr.consultation.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.ehr.clinicalNoteId
import no.nav.helse.ehr.consultation.ClinicalNote
import no.nav.helse.ehr.consultation.ClinicalNoteDeleteResult
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
            get("/{clinicalNoteId}/attachment") {
                val id = call.clinicalNoteId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                val attachment =
                    consultationService.getClinicalNoteAttachment(id, orgNumber)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    inlinePdfContentDisposition(attachment.title),
                )
                call.respondBytes(attachment.data, ContentType.Application.Pdf)
            }
            delete("/{clinicalNoteId}") {
                val id = call.clinicalNoteId()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                when (consultationService.deleteClinicalNote(id, orgNumber)) {
                    ClinicalNoteDeleteResult.DELETED -> call.respond(HttpStatusCode.NoContent)
                    ClinicalNoteDeleteResult.NOT_FOUND -> call.respond(HttpStatusCode.NotFound)
                    ClinicalNoteDeleteResult.PROTECTED ->
                        call.respond(
                            HttpStatusCode.Conflict,
                            "Journalnotater skrevet i journalsystemet kan ikke slettes",
                        )
                }
            }
            post {
                val request = call.receive<ClinicalNote>()
                val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
                if (consultationService.insertClinicalNote(request, orgNumber)) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(
                        HttpStatusCode.Conflict,
                        "Journalnotatet kan ikke endres fordi det er skrevet av en annen app",
                    )
                }
            }
        }
    }
}
