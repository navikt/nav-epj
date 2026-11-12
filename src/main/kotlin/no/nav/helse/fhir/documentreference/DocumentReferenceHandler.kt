@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Resource
import io.ktor.http.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ClinicalNoteDeleteResult
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.FhirResourceHandler
import no.nav.helse.fhir.FhirResult
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.fhirFailure
import no.nav.helse.fhir.invalidResource
import no.nav.helse.fhir.notFound
import no.nav.helse.fhir.parseReferenceId
import no.nav.helse.fhir.patientOrSubjectReferenceInputId
import no.nav.helse.fhir.requireAnyWriteScope
import no.nav.helse.fhir.requireCreateAccess
import no.nav.helse.fhir.requireMatchingId
import no.nav.helse.fhir.requireUpsertAccess
import no.nav.helse.fhir.security.clinicalOrgNumber
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.fhir.security.requireScope
import no.nav.helse.fhir.translatingWriteFailures
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.SmartPrincipal

private const val DOCUMENT_REFERENCE = "DocumentReference"

class DocumentReferenceHandler(
    private val service: DocumentReferenceService,
    private val fhirServerUrl: String,
) : FhirResourceHandler {
    override val resourceType = DOCUMENT_REFERENCE

    override suspend fun read(principal: SmartPrincipal, id: Uuid): FhirResult {
        principal.requireScope(resourceType, Interaction.READ)
        val documentReference =
            service.getDocumentReference(id, principal.clinicalOrgNumber())
                ?: notFound(resourceType, id)
        principal.requirePatientMatch(
            resourceType,
            Interaction.READ,
            documentReference.subject?.reference?.value?.substringAfter("Patient/"),
        )
        return FhirResult(HttpStatusCode.OK, documentReference)
    }

    override suspend fun search(principal: SmartPrincipal, parameters: Parameters): FhirResult {
        val patientId = parameters.patientOrSubjectReferenceInputId()
        principal.requireScope(resourceType, Interaction.SEARCH)
        principal.requirePatientMatch(resourceType, Interaction.SEARCH, patientId.value.toString())
        val encounterId = parameters["encounter"]?.let { parameters.encounterReferenceId() }
        val related =
            parameters["related"]?.let {
                parseReferenceId(it, "QuestionnaireResponse", resourceType, "related")
            }
        val bundle =
            service.searchDocumentReferences(
                PatientId(patientId.value),
                encounterId?.let { ConsultationId(it.value) },
                related,
                principal.clinicalOrgNumber(),
            )
        return FhirResult(HttpStatusCode.OK, bundle)
    }

    override suspend fun create(principal: SmartPrincipal, resource: Resource): FhirResult {
        principal.requireScope(resourceType, Interaction.CREATE)
        val write = resource.asDocumentReference().toClinicalDocumentWrite()
        principal.requireCreateAccess(resourceType, write.patientId.value)
        val id = Uuid.generateV4()
        val orgNumber = principal.clinicalOrgNumber()
        principal.translatingWriteFailures(resourceType) {
            service.write(id, write, orgNumber, allowCreate = true, allowUpdate = false)
        }
        return FhirResult(HttpStatusCode.Created, stored(id, orgNumber), location(id))
    }

    override suspend fun update(
        principal: SmartPrincipal,
        id: Uuid,
        resource: Resource,
    ): FhirResult {
        principal.requireAnyWriteScope(resourceType)
        val documentReference = resource.asDocumentReference()
        requireMatchingId(documentReference.id, id, resourceType)
        val write = documentReference.toClinicalDocumentWrite()
        val access = principal.requireUpsertAccess(resourceType, write.patientId.value)
        val orgNumber = principal.clinicalOrgNumber()
        val result =
            principal.translatingWriteFailures(resourceType) {
                service.write(id, write, orgNumber, access.canCreate, access.canUpdate)
            }
        val status = if (result.created) HttpStatusCode.Created else HttpStatusCode.OK
        return FhirResult(status, stored(id, orgNumber), location(id))
    }

    override suspend fun delete(principal: SmartPrincipal, id: Uuid): FhirResult {
        principal.requireScope(resourceType, Interaction.DELETE)
        val orgNumber = principal.clinicalOrgNumber()
        val patientId = service.findPatientId(id, orgNumber) ?: notFound(resourceType, id)
        principal.requirePatientMatch(resourceType, Interaction.DELETE, patientId.value.toString())
        return when (service.delete(id, patientId, orgNumber)) {
            ClinicalNoteDeleteResult.DELETED -> FhirResult(HttpStatusCode.NoContent)
            ClinicalNoteDeleteResult.NOT_FOUND -> notFound(resourceType, id)
            ClinicalNoteDeleteResult.PROTECTED ->
                fhirFailure(
                    HttpStatusCode.Conflict,
                    OperationOutcome.IssueType.Conflict,
                    "Journalnotatet er ikke opprettet via FHIR og kan ikke slettes",
                )
        }
    }

    private suspend fun stored(id: Uuid, orgNumber: String?): DocumentReference =
        service.getDocumentReference(id, orgNumber) ?: notFound(resourceType, id)

    private fun location(id: Uuid) = "$fhirServerUrl/$resourceType/$id"

    private fun Resource.asDocumentReference(): DocumentReference =
        this as? DocumentReference
            ?: invalidResource(
                OperationOutcome.IssueType.Invalid,
                "Forespørselen er ikke en DocumentReference",
            )
}
