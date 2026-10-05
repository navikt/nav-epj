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
import no.nav.helse.core.utils.AktivKonsultasjonNotFoundException
import no.nav.helse.core.utils.DuplikatPasientException
import no.nav.helse.core.utils.HelsepersonellForPatientNotFoundException
import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.LegekontorNotfoundException
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.core.utils.UgyldigPersonidentException
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.security.InsufficientScopeException
import no.nav.helse.fhir.security.PatientMismatchException

fun Application.configureStatusPages() {
    val log = logger()
    install(StatusPages) {
        exception<KonsultasjonNotFoundException> { call, cause ->
            call.respondText(
                text = "Konsultasjon not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<KonsultasjonNotFoundForPatientException> { call, cause ->
            call.respondText(
                text = "Konsultasjon not found for patient: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<AktivKonsultasjonNotFoundException> { call, cause ->
            call.respondText(
                text = "Aktiv konsultasjon not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<HelsepersonellNotFoundException> { call, cause ->
            call.respondText(
                text = "Helsepersonell not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<LegekontorNotfoundException> { call, cause ->
            call.respondText(
                text = "Legekontor not found: ${cause.message}",
                status = HttpStatusCode.NotFound,
            )
        }
        exception<UgyldigDiagnoseException> { call, cause ->
            call.respondText(
                text = "Ugyldig diagnose: ${cause.message}",
                status = HttpStatusCode.BadRequest,
            )
        }
        exception<UgyldigPersonidentException> { call, cause ->
            call.respondText(text = "Ugyldig personident", status = HttpStatusCode.BadRequest)
        }
        exception<DuplikatPasientException> { call, cause ->
            call.respondText(text = "Pasienten finnes allerede", status = HttpStatusCode.Conflict)
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
        exception<PasientCreationException> { call, cause ->
            log.error("Pasient ble ikke opprettet", cause)
            call.respondText(
                text = "En uventet feil oppstod ved opprettelse av pasient",
                status = HttpStatusCode.InternalServerError,
            )
        }
        exception<HelsepersonellForPatientNotFoundException> { call, cause ->
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
