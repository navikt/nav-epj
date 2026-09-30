package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Observation
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.String as FhirString
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import no.nav.helse.core.utils.DuplikatMaalingException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.observationId
import no.nav.helse.fhir.patientOrSubjectReferenceInputId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

private const val LOINC_SYSTEM = "http://loinc.org"

fun Route.observationRoutes(
    observationService: ObservationService,
    fhirR4Json: FhirR4Json,
    fhirContentType: ContentType,
    fhirServerUrl: String,
) {
    route("/fhir") {
        get("/Observation/{observation}") {
            val id = call.observationId()
            val principal = call.requireFhirScope("Observation", Interaction.READ)

            val observation =
                observationService.getObservationById(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        "No observation found for $id",
                    )
            principal.requirePatientMatch(
                "Observation",
                Interaction.READ,
                observation.subject?.reference?.value?.substringAfter("Patient/"),
            )

            call.respondText(fhirR4Json.encodeToString(observation), fhirContentType)
        }

        get("/Observation") {
            val patientId = call.patientOrSubjectReferenceInputId()
            val principal = call.requireFhirScope("Observation", Interaction.SEARCH)
            principal.requirePatientMatch(
                "Observation",
                Interaction.SEARCH,
                patientId.value.toString(),
            )

            val encounterId = call.parameters["encounter"]?.let { call.encounterReferenceId() }
            val code = call.parameters["code"]?.let(::parseObservationSearchCode)

            val bundle = observationService.searchObservations(patientId, encounterId, code)
            call.respondText(fhirR4Json.encodeToString(bundle), fhirContentType)
        }

        post("/Observation") {
            val principal = call.requireFhirScope("Observation", Interaction.CREATE)
            val body = call.receiveText()

            val observation =
                try {
                    fhirR4Json.decodeFromString(body) as Observation
                } catch (exception: SerializationException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Structure,
                        "Klarte ikke å tolke forespørselen som en FHIR Observation: " +
                            "${exception.message}",
                    )
                } catch (exception: ClassCastException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Structure,
                        "Forespørselen er ikke en Observation: ${exception.message}",
                    )
                } catch (exception: IllegalArgumentException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Structure,
                        "Klarte ikke å tolke forespørselen som en FHIR Observation: " +
                            "${exception.message}",
                    )
                }

            val request =
                try {
                    observation.toOpprettMaalingRequest()
                } catch (exception: InvalidObservationException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        exception.issueType,
                        exception.message ?: "Ugyldig Observation",
                    )
                }

            principal.requirePatientMatch(
                "Observation",
                Interaction.CREATE,
                request.pasientId.value.toString(),
            )

            val created =
                try {
                    observationService.createObservation(request)
                } catch (exception: KonsultasjonNotFoundException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Not_Found,
                        exception.message ?: "Fant ikke encounter",
                    )
                } catch (exception: KonsultasjonTilhorerAnnenPasientException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Invalid,
                        exception.message ?: "Encounter tilhører en annen pasient",
                    )
                } catch (exception: DuplikatMaalingException) {
                    return@post call.respondOperationOutcome(
                        fhirR4Json,
                        fhirContentType,
                        HttpStatusCode.Conflict,
                        OperationOutcome.IssueType.Duplicate,
                        exception.message ?: "Observation finnes allerede",
                    )
                }

            call.response.header(HttpHeaders.Location, "$fhirServerUrl/Observation/${created.id}")
            call.respondText(
                fhirR4Json.encodeToString(created),
                fhirContentType,
                HttpStatusCode.Created,
            )
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

/**
 * Accepts either a bare LOINC code (`code=8310-5`) or a fully qualified `system|code` token
 * (`code=http://loinc.org|8310-5`). Any other system is rejected explicitly rather than silently
 * ignored, since LOINC is the only code system Maaling stores.
 */
private fun parseObservationSearchCode(raw: String): String {
    if (raw.isBlank()) {
        throw BadRequestException("Parameteren 'code' kan ikke være tom")
    }
    val separatorIndex = raw.indexOf('|')
    if (separatorIndex < 0) return raw

    val system = raw.substring(0, separatorIndex)
    val code = raw.substring(separatorIndex + 1)
    if (system != LOINC_SYSTEM || code.isEmpty()) {
        throw BadRequestException(
            "Parameteren 'code' må enten være en bar LOINC-kode eller '$LOINC_SYSTEM|<kode>'"
        )
    }
    return code
}
