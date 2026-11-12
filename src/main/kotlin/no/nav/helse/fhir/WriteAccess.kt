package no.nav.helse.fhir

import com.google.fhir.model.r4.OperationOutcome
import io.ktor.http.*
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.AuthorNotOnConsultationException
import no.nav.helse.core.utils.ClinicalDocumentConflictException
import no.nav.helse.core.utils.ClinicalDocumentWriteNotAllowedException
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.DuplicateClinicalNoteException
import no.nav.helse.fhir.security.InsufficientScopeException
import no.nav.helse.fhir.security.PatientMismatchException
import no.nav.helse.fhir.security.hasScope
import no.nav.helse.fhir.security.permitsPatient
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.fhir.security.requireScope
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.SmartPrincipal

class WriteAccess(val canCreate: Boolean, val canUpdate: Boolean)

fun SmartPrincipal.requireAnyWriteScope(resourceType: String) {
    if (
        !hasScope(resourceType, Interaction.CREATE) && !hasScope(resourceType, Interaction.UPDATE)
    ) {
        throw InsufficientScopeException(resourceType, Interaction.UPDATE)
    }
}

fun SmartPrincipal.requireCreateAccess(resourceType: String, patientId: Uuid) {
    requireScope(resourceType, Interaction.CREATE)
    requirePatientMatch(resourceType, Interaction.CREATE, patientId.toString())
}

fun SmartPrincipal.requireUpsertAccess(resourceType: String, patientId: Uuid): WriteAccess {
    val access =
        WriteAccess(
            canCreate = canWrite(resourceType, Interaction.CREATE, patientId),
            canUpdate = canWrite(resourceType, Interaction.UPDATE, patientId),
        )
    if (access.canCreate || access.canUpdate) return access
    requireAnyWriteScope(resourceType)
    throw PatientMismatchException(resourceType)
}

private fun SmartPrincipal.canWrite(
    resourceType: String,
    interaction: Interaction,
    patientId: Uuid,
): Boolean =
    hasScope(resourceType, interaction) &&
        permitsPatient(resourceType, interaction, patientId.toString())

fun notFound(resourceType: String, id: Uuid): Nothing =
    fhirFailure(
        HttpStatusCode.NotFound,
        OperationOutcome.IssueType.Not_Found,
        "Fant ikke $resourceType/$id",
    )

fun requireMatchingId(resourceId: String?, urlId: Uuid, resourceType: String) {
    val bodyId =
        resourceId
            ?: invalidResource(OperationOutcome.IssueType.Required, "$resourceType.id er påkrevd")
    val matches = runCatching { Uuid.parse(bodyId) == urlId }.getOrDefault(false)
    if (!matches) {
        invalidResource(
            OperationOutcome.IssueType.Invalid,
            "$resourceType.id må være lik id-en i URL-en",
        )
    }
}

suspend fun <T> SmartPrincipal.translatingWriteFailures(
    resourceType: String,
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (exception: ClinicalDocumentWriteNotAllowedException) {
        val interaction = if (exception.creating) Interaction.CREATE else Interaction.UPDATE
        if (hasScope(resourceType, interaction)) throw PatientMismatchException(resourceType)
        throw InsufficientScopeException(resourceType, interaction)
    } catch (exception: ConsultationNotFoundException) {
        fhirFailure(
            HttpStatusCode.BadRequest,
            OperationOutcome.IssueType.Not_Found,
            exception.message ?: "Fant ikke encounter",
        )
    } catch (exception: ConsultationBelongsToOtherPatientException) {
        invalidResource(
            OperationOutcome.IssueType.Invalid,
            exception.message ?: "Encounter tilhører en annen pasient",
        )
    } catch (exception: AuthorNotOnConsultationException) {
        invalidResource(OperationOutcome.IssueType.Invalid, exception.message.orEmpty())
    } catch (exception: DuplicateClinicalNoteException) {
        fhirFailure(
            HttpStatusCode.Conflict,
            OperationOutcome.IssueType.Duplicate,
            exception.message.orEmpty(),
        )
    } catch (exception: ClinicalDocumentConflictException) {
        fhirFailure(
            HttpStatusCode.Conflict,
            OperationOutcome.IssueType.Conflict,
            exception.message.orEmpty(),
        )
    }
