package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.Uri
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId

class QuestionnaireResponseService(private val repository: QuestionnaireResponseRepository) {
    private val fhirJson = FhirR4Json()

    fun toWrite(
        questionnaireResponse: QuestionnaireResponse,
        id: Uuid,
    ): QuestionnaireResponseWrite = questionnaireResponse.toQuestionnaireResponseWrite(fhirJson, id)

    suspend fun write(
        id: Uuid,
        write: QuestionnaireResponseWrite,
        orgNumber: String?,
        allowCreate: Boolean,
        allowUpdate: Boolean,
    ): Boolean = repository.write(id, write, orgNumber, allowCreate, allowUpdate)

    suspend fun get(id: Uuid, orgNumber: String?): QuestionnaireResponse? =
        repository.find(id, orgNumber)?.toResource()

    suspend fun findPatientId(id: Uuid, orgNumber: String?): PatientId? =
        repository.find(id, orgNumber)?.patientId

    suspend fun delete(id: Uuid, patientId: PatientId, orgNumber: String?): Boolean =
        repository.delete(id, patientId, orgNumber)

    suspend fun search(
        patientId: PatientId,
        consultationId: ConsultationId?,
        orgNumber: String?,
    ): Bundle =
        Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                repository.list(patientId, consultationId, orgNumber).map {
                    Bundle.Entry(
                        fullUrl = Uri(value = "QuestionnaireResponse/${it.id}"),
                        resource = it.toResource(),
                    )
                },
        )

    private fun StoredQuestionnaireResponse.toResource(): QuestionnaireResponse =
        fhirJson.decodeFromString(resourceJson) as QuestionnaireResponse
}
