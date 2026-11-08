package no.nav.helse.plugins

import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.String as FhirString
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import no.nav.helse.core.utils.ActiveConsultationNotFoundException
import no.nav.helse.core.utils.ClinicNotFoundException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationNotFoundForPatientException
import no.nav.helse.core.utils.DuplicateDiagnosisException
import no.nav.helse.core.utils.DuplicatePatientException
import no.nav.helse.core.utils.InvalidDiagnosisException
import no.nav.helse.core.utils.InvalidPersonidentException
import no.nav.helse.core.utils.NoFastlegelisteException
import no.nav.helse.core.utils.PatientCreationException
import no.nav.helse.core.utils.PractitionerForPatientNotFoundException
import no.nav.helse.core.utils.PractitionerNotFoundException
import no.nav.helse.core.utils.SessionOrgRequiredException
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.security.InsufficientScopeException
import no.nav.helse.fhir.security.OrganizationContextMissingException
import no.nav.helse.fhir.security.PatientMismatchException

fun Application.configureStatusPages() {
    val log = logger()
    install(StatusPages) {
        exception<ConsultationNotFoundException> { call, cause ->
            call.respondText(
                text = "Konsultasjon not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<ConsultationNotFoundForPatientException> { call, cause ->
            call.respondText(
                text = "Konsultasjon not found for patient: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<ActiveConsultationNotFoundException> { call, cause ->
            call.respondText(
                text = "Aktiv konsultasjon not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<PractitionerNotFoundException> { call, cause ->
            call.respondText(
                text = "Helsepersonell not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<ClinicNotFoundException> { call, cause ->
            call.respondText(
                text = "Legekontor not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<NoFastlegelisteException> { call, cause ->
            call.respondText(text = cause.message.orEmpty(), status = HttpStatusCode.Forbidden)
        }
        exception<SessionOrgRequiredException> { call, cause ->
            call.respondText(
                text = "Legekontor ikke valgt: ${cause.message}",
                status = HttpStatusCode.Conflict,
            )
        }
        exception<InvalidDiagnosisException> { call, cause ->
            call.respondText(
                text = "Ugyldig diagnose: ${cause.message}",
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<DuplicateDiagnosisException> { call, cause ->
            call.respondText(
                text = "Ugyldig diagnose: ${cause.message}",
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<InvalidPersonidentException> { call, cause ->
            call.respondText(text = "Ugyldig personident", status = HttpStatusCode.BadRequest)
        }
        exception<DuplicatePatientException> { call, cause ->
            call.respondText(text = "Pasienten finnes allerede", status = HttpStatusCode.Conflict)
        }
        exception<OrganizationContextMissingException> { call, cause ->
            call.respondAuthorizationFailure(
                status = HttpStatusCode.Forbidden,
                issueType = OperationOutcome.IssueType.Forbidden,
                text = cause.message ?: "Forbidden",
            )
        }
        exception<InsufficientScopeException> { call, cause ->
            call.response.header(
                HttpHeaders.WWWAuthenticate,
                "Bearer error=\"insufficient_scope\", scope=\"${cause.resourceType}.${cause.interaction.code}\"",
            )
            call.respondAuthorizationFailure(
                status = HttpStatusCode.Forbidden,
                issueType = OperationOutcome.IssueType.Forbidden,
                text = cause.message ?: "Forbidden",
            )
        }
        exception<PatientMismatchException> { call, cause ->
            call.respondAuthorizationFailure(
                status = HttpStatusCode.NotFound,
                issueType = OperationOutcome.IssueType.Not_Found,
                text = "Not found",
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Ugyldig forespørsel",
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<PatientCreationException> { call, cause ->
            log.error("Pasient ble ikke opprettet", cause)
            call.respondText(
                text = "En uventet feil oppstod ved opprettelse av pasient",
                status = HttpStatusCode.InternalServerError,
            )
        }
        exception<PractitionerForPatientNotFoundException> { call, cause ->
            log.error("Uventet feil i helsepersonell-API", cause)
            call.respondText(
                text = "En uventet feil oppstod",
                status = HttpStatusCode.InternalServerError,
            )
        }
        exception<Throwable> { call, cause ->
            log.error("Uventet feil", cause)
            call.respondText(
                text = "En uventet feil oppstod",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}

private val fhirR4Json = FhirR4Json()
private val fhirContentType = ContentType("application", "fhir+json")

private fun ApplicationCall.isFhirPath(): Boolean {
    val path = request.path()
    return path == "/fhir" || path.startsWith("/fhir/")
}

private suspend fun ApplicationCall.respondAuthorizationFailure(
    status: HttpStatusCode,
    issueType: OperationOutcome.IssueType,
    text: String,
) {
    if (!isFhirPath()) return respondText(text = text, status = status)
    val outcome =
        OperationOutcome(
            issue =
                listOf(
                    OperationOutcome.Issue(
                        severity = Enumeration(value = OperationOutcome.IssueSeverity.Error),
                        code = Enumeration(value = issueType),
                        diagnostics = FhirString(value = text),
                    )
                )
        )
    respondText(fhirR4Json.encodeToString(outcome), fhirContentType, status)
}
