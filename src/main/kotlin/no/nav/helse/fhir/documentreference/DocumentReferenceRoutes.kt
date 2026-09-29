package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.String as FhirString
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import no.nav.helse.core.utils.DuplikatJournalnotatException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.documentReferenceId
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.patientOrSubjectReferenceInputId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

fun Route.documentReferenceRoutes(
    documentReferenceService: DocumentReferenceService,
    fhirjson: FhirR4Json,
    fhirContentType: ContentType,
    fhirServerUrl: String,
) {
    val log = logger()
    route("/fhir") {
        get("/DocumentReference/{documentreferenceId}") {
            val id = call.documentReferenceId()
            val principal = call.requireFhirScope("DocumentReference", Interaction.READ)

            val documentReference =
                documentReferenceService.getDocumentReferences(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        "No documentReference found for $id",
                    )
            principal.requirePatientMatch(
                "DocumentReference",
                Interaction.READ,
                documentReference.subject?.reference?.value?.substringAfter("Patient/"),
            )

            val json = fhirjson.encodeToString(documentReference).replace("\"no-NO\"", "\"NO-nb\"")
            call.respondText(json, fhirContentType)
        }
        get("/DocumentReference") {
            val patientId = call.patientOrSubjectReferenceInputId()
            val principal = call.requireFhirScope("DocumentReference", Interaction.SEARCH)
            principal.requirePatientMatch(
                "DocumentReference",
                Interaction.SEARCH,
                patientId.value.toString(),
            )

            val encounterId = call.parameters["encounter"]?.let { call.encounterReferenceId() }

            val bundle = documentReferenceService.searchDocumentReferences(patientId, encounterId)
            val json = fhirjson.encodeToString(bundle).replace("\"no-NO\"", "\"NO-nb\"")
            call.respondText(json, fhirContentType)
        }
        post("/DocumentReference") {
            val principal = call.requireFhirScope("DocumentReference", Interaction.CREATE)
            val body = call.receiveText()
            val bodyWithReplacement = body.replace("\"NO-nb\"", "\"no-NO\"")

            val documentReference =
                try {
                    fhirjson.decodeFromString(bodyWithReplacement) as DocumentReference
                } catch (exception: SerializationException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Structure,
                        "Klarte ikke å tolke forespørselen som en FHIR DocumentReference: " +
                            "${exception.message}",
                    )
                } catch (exception: ClassCastException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Structure,
                        "Forespørselen er ikke en DocumentReference: ${exception.message}",
                    )
                } catch (exception: IllegalArgumentException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Structure,
                        "Klarte ikke å tolke forespørselen som en FHIR DocumentReference: " +
                            "${exception.message}",
                    )
                }

            val request =
                try {
                    documentReference.toOpprettJournalnotatRequest()
                } catch (exception: InvalidDocumentReferenceException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        exception.issueType,
                        exception.message ?: "Ugyldig DocumentReference",
                    )
                }

            principal.requirePatientMatch(
                "DocumentReference",
                Interaction.CREATE,
                request.pasientId.value.toString(),
            )

            val created =
                try {
                    documentReferenceService.createDocumentReference(request)
                } catch (exception: KonsultasjonNotFoundException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Not_Found,
                        exception.message ?: "Fant ikke encounter",
                    )
                } catch (exception: KonsultasjonTilhorerAnnenPasientException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Invalid,
                        exception.message ?: "Encounter tilhører en annen pasient",
                    )
                } catch (exception: DuplikatJournalnotatException) {
                    return@post call.respondOperationOutcome(
                        fhirjson,
                        fhirContentType,
                        HttpStatusCode.Conflict,
                        OperationOutcome.IssueType.Duplicate,
                        exception.message ?: "DocumentReference finnes allerede",
                    )
                }

            call.response.header(
                HttpHeaders.Location,
                "$fhirServerUrl/DocumentReference/${created.id}",
            )
            val json = fhirjson.encodeToString(created).replace("\"no-NO\"", "\"NO-nb\"")
            call.respondText(json, fhirContentType, HttpStatusCode.Created)
        }
        put("/DocumentReference/{documentReferenceId}") {
            val id = call.documentReferenceId()
            log.info("Updating documentReference with id: $id")
            val body = call.receiveText()
            val bodyWithReplacement = body.replace("\"NO-nb\"", "\"no-NO\"")
            val documentReference =
                fhirjson.decodeFromString(bodyWithReplacement) as DocumentReference

            val principal = call.requireFhirScope("DocumentReference", Interaction.CREATE)
            principal.requirePatientMatch(
                "DocumentReference",
                Interaction.CREATE,
                documentReference.subject?.reference?.value?.substringAfter("Patient/"),
            )

            val created = documentReferenceService.createDocumentReference(documentReference)
            if (created) {
                val json =
                    fhirjson.encodeToString(documentReference).replace("\"no-NO\"", "\"NO-nb\"")
                call.respondText(json, fhirContentType)
            } else {
                call.respond(HttpStatusCode.Conflict)
            }
        }
        put("/QuestionnaireResponse/{documentReferenceId}") {
            val body = call.receiveText()
            val questionnaireResponse = fhirjson.decodeFromString(body) as QuestionnaireResponse
            call.respondText(fhirjson.encodeToString(questionnaireResponse), fhirContentType)
        }
    }
}

private suspend fun ApplicationCall.respondOperationOutcome(
    fhirR4Json: FhirR4Json,
    fhirContentType: ContentType,
    status: HttpStatusCode,
    issueType: OperationOutcome.IssueType,
    diagnostics: String,
) {
    val outcome =
        OperationOutcome(
            issue =
                listOf(
                    OperationOutcome.Issue(
                        severity = Enumeration(value = OperationOutcome.IssueSeverity.Error),
                        code = Enumeration(value = issueType),
                        diagnostics = FhirString(value = diagnostics),
                    )
                )
        )
    respondText(fhirR4Json.encodeToString(outcome), fhirContentType, status)
}
