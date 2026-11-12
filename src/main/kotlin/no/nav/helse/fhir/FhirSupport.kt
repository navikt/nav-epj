package no.nav.helse.fhir

import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.String as FhirString
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlin.uuid.Uuid
import kotlinx.io.readByteArray
import no.nav.helse.smart.security.SmartPrincipal

const val MAX_FHIR_WRITE_BODY_BYTES = 3_500_000L

class FhirRequestException(
    val status: HttpStatusCode,
    val issueType: OperationOutcome.IssueType,
    message: String,
) : RuntimeException(message)

class FhirResult(
    val status: HttpStatusCode,
    val resource: Resource? = null,
    val location: String? = null,
)

interface FhirResourceHandler {
    val resourceType: String

    suspend fun read(principal: SmartPrincipal, id: Uuid): FhirResult

    suspend fun search(principal: SmartPrincipal, parameters: Parameters): FhirResult

    suspend fun create(principal: SmartPrincipal, resource: Resource): FhirResult

    suspend fun update(principal: SmartPrincipal, id: Uuid, resource: Resource): FhirResult

    suspend fun delete(principal: SmartPrincipal, id: Uuid): FhirResult
}

fun fhirFailure(
    status: HttpStatusCode,
    issueType: OperationOutcome.IssueType,
    message: String,
): Nothing = throw FhirRequestException(status, issueType, message)

fun invalidResource(issueType: OperationOutcome.IssueType, message: String): Nothing =
    fhirFailure(HttpStatusCode.BadRequest, issueType, message)

fun operationOutcome(issueType: OperationOutcome.IssueType, diagnostics: String): OperationOutcome =
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

fun parseReferenceId(reference: String?, resourceType: String, owner: String, field: String): Uuid {
    val prefix = "$resourceType/"
    if (reference == null) {
        invalidResource(OperationOutcome.IssueType.Required, "$owner.$field er påkrevd")
    }
    if (!reference.startsWith(prefix)) {
        invalidResource(
            OperationOutcome.IssueType.Invalid,
            "$owner.$field.reference må starte med '$prefix'",
        )
    }
    return try {
        Uuid.parse(reference.removePrefix(prefix))
    } catch (exception: IllegalArgumentException) {
        invalidResource(
            OperationOutcome.IssueType.Invalid,
            "$owner.$field.reference er ikke en gyldig UUID: ${exception.message}",
        )
    }
}

fun parsePractitionerHpr(reference: String?, owner: String): String =
    reference
        ?.takeIf { it.startsWith("Practitioner/") }
        ?.removePrefix("Practitioner/")
        ?.takeIf { it.isNotBlank() }
        ?: invalidResource(
            OperationOutcome.IssueType.Invalid,
            "$owner.author.reference må være 'Practitioner/{hpr}'",
        )

suspend fun ApplicationCall.receiveBoundedText(maxBytes: Long = MAX_FHIR_WRITE_BODY_BYTES): String {
    val declaredLength = request.contentLength()
    if (declaredLength != null && declaredLength > maxBytes) tooLarge(maxBytes)
    val bytes = receiveChannel().readBuffer(maxBytes + 1).readByteArray()
    if (bytes.size > maxBytes) tooLarge(maxBytes)
    return bytes.decodeToString()
}

private fun tooLarge(maxBytes: Long): Nothing =
    fhirFailure(
        HttpStatusCode.PayloadTooLarge,
        OperationOutcome.IssueType.Too_Long,
        "Forespørselen er større enn $maxBytes bytes",
    )
