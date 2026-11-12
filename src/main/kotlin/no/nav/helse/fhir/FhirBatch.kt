package no.nav.helse.fhir

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.smart.security.SmartPrincipal

const val MAX_BATCH_ENTRIES = 10

private class BatchTarget(
    val handler: FhirResourceHandler,
    val id: Uuid?,
    val parameters: Parameters,
)

private val FORBIDDEN_URL_PARTS = listOf("://", "..", "#")

class FhirBatchProcessor(handlers: List<FhirResourceHandler>) {
    private val log = logger()
    private val handlers = handlers.associateBy { it.resourceType }

    suspend fun process(principal: SmartPrincipal, bundle: Bundle): Bundle {
        if (bundle.type.value != Bundle.BundleType.Batch) {
            fhirFailure(
                HttpStatusCode.BadRequest,
                OperationOutcome.IssueType.Not_Supported,
                "Kun Bundle av typen 'batch' støttes",
            )
        }
        if (bundle.entry.size > MAX_BATCH_ENTRIES) {
            fhirFailure(
                HttpStatusCode.BadRequest,
                OperationOutcome.IssueType.Too_Costly,
                "En batch kan ikke inneholde mer enn $MAX_BATCH_ENTRIES oppføringer",
            )
        }
        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Batch_Response),
            entry = bundle.entry.map { processEntry(principal, it) },
        )
    }

    private suspend fun processEntry(principal: SmartPrincipal, entry: Bundle.Entry): Bundle.Entry =
        try {
            responseEntry(execute(principal, entry))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            failureEntry(exception.toFailure())
        }

    private suspend fun execute(principal: SmartPrincipal, entry: Bundle.Entry): FhirResult {
        val request =
            entry.request
                ?: invalidResource(
                    OperationOutcome.IssueType.Required,
                    "Oppføringen mangler request",
                )
        val target = request.url.value.orEmpty().toTarget()
        val resource = entry.resource
        return when (request.method.value) {
            Bundle.HTTPVerb.Get ->
                if (target.id != null) {
                    target.handler.read(principal, target.id)
                } else {
                    target.handler.search(principal, target.parameters)
                }
            Bundle.HTTPVerb.Post -> {
                if (target.id != null)
                    invalidResource(
                        OperationOutcome.IssueType.Invalid,
                        "POST kan ikke ha id i URL-en",
                    )
                target.handler.create(principal, requireResource(resource))
            }
            Bundle.HTTPVerb.Put ->
                target.handler.update(principal, requireId(target), requireResource(resource))
            Bundle.HTTPVerb.Delete -> target.handler.delete(principal, requireId(target))
            else ->
                invalidResource(
                    OperationOutcome.IssueType.Not_Supported,
                    "HTTP-metoden støttes ikke i batch",
                )
        }
    }

    private fun String.toTarget(): BatchTarget {
        if (!isRelativeReference()) {
            invalidResource(OperationOutcome.IssueType.Invalid, "URL-en må være relativ: $this")
        }
        val path = substringBefore('?')
        val segments = path.split('/')
        val handler =
            handlers[segments.first()]
                ?: invalidResource(
                    OperationOutcome.IssueType.Not_Supported,
                    "Ressurstypen støttes ikke i batch: ${segments.first()}",
                )
        if (segments.size > 2) {
            invalidResource(OperationOutcome.IssueType.Invalid, "Ugyldig URL: $this")
        }
        val id =
            segments.getOrNull(1)?.let {
                Uuid.parseOrNull(it)
                    ?: invalidResource(
                        OperationOutcome.IssueType.Invalid,
                        "Id-en er ikke en gyldig UUID",
                    )
            }
        return BatchTarget(handler, id, parseQueryString(substringAfter('?', "")))
    }

    private fun String.isRelativeReference() =
        isNotBlank() && !startsWith("/") && !FORBIDDEN_URL_PARTS.any { it in this }

    private fun requireId(target: BatchTarget): Uuid =
        target.id ?: invalidResource(OperationOutcome.IssueType.Required, "URL-en mangler id")

    private fun requireResource(resource: Resource?): Resource =
        resource
            ?: invalidResource(OperationOutcome.IssueType.Required, "Oppføringen mangler resource")

    private fun Exception.toFailure(): FhirRequestException =
        toFhirFailure()
            ?: when (this) {
                is BadRequestException ->
                    FhirRequestException(
                        HttpStatusCode.BadRequest,
                        OperationOutcome.IssueType.Invalid,
                        message ?: "Ugyldig forespørsel",
                    )
                else -> {
                    log.error("Uventet feil i batch-oppføring", this)
                    FhirRequestException(
                        HttpStatusCode.InternalServerError,
                        OperationOutcome.IssueType.Exception,
                        "En uventet feil oppstod",
                    )
                }
            }

    private fun responseEntry(result: FhirResult) =
        Bundle.Entry(
            resource = result.resource,
            response =
                Bundle.Entry.Response(
                    status = FhirString(value = result.status.toBatchStatus()),
                    location = result.location?.let { Uri(value = it) },
                ),
        )

    private fun failureEntry(failure: FhirRequestException) =
        Bundle.Entry(
            response =
                Bundle.Entry.Response(
                    status = FhirString(value = failure.status.toBatchStatus()),
                    outcome = operationOutcome(failure.issueType, failure.message.orEmpty()),
                )
        )

    private fun HttpStatusCode.toBatchStatus() = "$value $description"
}

fun Route.fhirBatchRoutes(
    processor: FhirBatchProcessor,
    codec: FhirCodec,
    fhirContentType: ContentType,
) {
    suspend fun ApplicationCall.handleBatch() {
        val principal = principal<SmartPrincipal>()!!
        val bundle =
            codec.decode(receiveBoundedText()) as? Bundle
                ?: invalidResource(
                    OperationOutcome.IssueType.Invalid,
                    "Forespørselen er ikke en Bundle",
                )
        respondText(codec.encode(processor.process(principal, bundle)), fhirContentType)
    }

    post("/fhir") { call.handleBatch() }
    post("/fhir/") { call.handleBatch() }
}
