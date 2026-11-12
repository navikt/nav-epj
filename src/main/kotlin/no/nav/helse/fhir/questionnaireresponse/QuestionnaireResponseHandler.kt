@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.Resource
import io.ktor.http.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.FhirResourceHandler
import no.nav.helse.fhir.FhirResult
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.invalidResource
import no.nav.helse.fhir.notFound
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

private const val QUESTIONNAIRE_RESPONSE = "QuestionnaireResponse"

class QuestionnaireResponseHandler(
    private val service: QuestionnaireResponseService,
    private val fhirServerUrl: String,
) : FhirResourceHandler {
    override val resourceType = QUESTIONNAIRE_RESPONSE

    override suspend fun read(principal: SmartPrincipal, id: Uuid): FhirResult {
        principal.requireScope(resourceType, Interaction.READ)
        val questionnaireResponse =
            service.get(id, principal.clinicalOrgNumber()) ?: notFound(resourceType, id)
        principal.requirePatientMatch(
            resourceType,
            Interaction.READ,
            questionnaireResponse.subject?.reference?.value?.substringAfter("Patient/"),
        )
        return FhirResult(HttpStatusCode.OK, questionnaireResponse)
    }

    override suspend fun search(principal: SmartPrincipal, parameters: Parameters): FhirResult {
        val patientId = parameters.patientOrSubjectReferenceInputId()
        principal.requireScope(resourceType, Interaction.SEARCH)
        principal.requirePatientMatch(resourceType, Interaction.SEARCH, patientId.value.toString())
        val encounterId = parameters["encounter"]?.let { parameters.encounterReferenceId() }
        val bundle =
            service.search(
                PatientId(patientId.value),
                encounterId?.let { ConsultationId(it.value) },
                principal.clinicalOrgNumber(),
            )
        return FhirResult(HttpStatusCode.OK, bundle)
    }

    override suspend fun create(principal: SmartPrincipal, resource: Resource): FhirResult {
        principal.requireScope(resourceType, Interaction.CREATE)
        val id = Uuid.generateV4()
        val write = service.toWrite(resource.asQuestionnaireResponse(), id)
        principal.requireCreateAccess(resourceType, write.patientId.value)
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
        val questionnaireResponse = resource.asQuestionnaireResponse()
        requireMatchingId(questionnaireResponse.id, id, resourceType)
        val write = service.toWrite(questionnaireResponse, id)
        val access = principal.requireUpsertAccess(resourceType, write.patientId.value)
        val orgNumber = principal.clinicalOrgNumber()
        val created =
            principal.translatingWriteFailures(resourceType) {
                service.write(id, write, orgNumber, access.canCreate, access.canUpdate)
            }
        val status = if (created) HttpStatusCode.Created else HttpStatusCode.OK
        return FhirResult(status, stored(id, orgNumber), location(id))
    }

    override suspend fun delete(principal: SmartPrincipal, id: Uuid): FhirResult {
        principal.requireScope(resourceType, Interaction.DELETE)
        val orgNumber = principal.clinicalOrgNumber()
        val patientId = service.findPatientId(id, orgNumber) ?: notFound(resourceType, id)
        principal.requirePatientMatch(resourceType, Interaction.DELETE, patientId.value.toString())
        if (!service.delete(id, patientId, orgNumber)) notFound(resourceType, id)
        return FhirResult(HttpStatusCode.NoContent)
    }

    private suspend fun stored(id: Uuid, orgNumber: String?): QuestionnaireResponse =
        service.get(id, orgNumber) ?: notFound(resourceType, id)

    private fun location(id: Uuid) = "$fhirServerUrl/$resourceType/$id"

    private fun Resource.asQuestionnaireResponse(): QuestionnaireResponse =
        this as? QuestionnaireResponse
            ?: invalidResource(
                OperationOutcome.IssueType.Invalid,
                "Forespørselen er ikke en QuestionnaireResponse",
            )
}
