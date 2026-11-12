package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Uri
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ClinicalDocument
import no.nav.helse.ehr.consultation.ClinicalDocumentRepository
import no.nav.helse.ehr.consultation.ClinicalDocumentWrite
import no.nav.helse.ehr.consultation.ClinicalDocumentWriteResult
import no.nav.helse.ehr.consultation.ClinicalNoteDeleteResult
import no.nav.helse.ehr.consultation.ClinicalNoteId
import no.nav.helse.ehr.consultation.ClinicalNoteSource
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId

class DocumentReferenceService(
    private val consultationService: ConsultationService,
    private val clinicalDocumentRepository: ClinicalDocumentRepository,
) {

    suspend fun write(
        id: Uuid,
        write: ClinicalDocumentWrite,
        orgNumber: String?,
        allowCreate: Boolean,
        allowUpdate: Boolean,
    ): ClinicalDocumentWriteResult =
        clinicalDocumentRepository.write(
            ClinicalNoteId(id),
            write,
            orgNumber,
            allowCreate,
            allowUpdate,
        )

    suspend fun delete(
        id: Uuid,
        patientId: PatientId,
        orgNumber: String?,
    ): ClinicalNoteDeleteResult =
        clinicalDocumentRepository.deleteFhirDocument(ClinicalNoteId(id), patientId, orgNumber)

    suspend fun findPatientId(id: Uuid, orgNumber: String?): PatientId? =
        consultationService.getClinicalNote(ClinicalNoteId(id), orgNumber)?.patientId

    suspend fun getDocumentReference(id: Uuid, orgNumber: String?): DocumentReference? {
        val document = clinicalDocumentRepository.find(ClinicalNoteId(id), orgNumber) ?: return null
        val hprs =
            if (document.note.source == ClinicalNoteSource.EHR) {
                consultationService.getConsultation(document.note.consultationId, orgNumber).hpr
            } else {
                document.authorHprs
            }
        return document.toDocumentReference(hprs)
    }

    suspend fun searchDocumentReferences(
        patientId: PatientId,
        consultationId: ConsultationId?,
        relatedQuestionnaireResponseId: Uuid?,
        orgNumber: String?,
    ): Bundle {
        val documents =
            clinicalDocumentRepository.list(
                patientId,
                consultationId,
                relatedQuestionnaireResponseId,
                orgNumber,
            )
        val hprsByConsultation =
            consultationService.getConsultations(patientId, orgNumber).associate { it.id to it.hpr }
        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry = documents.map { it.toSearchEntry(hprsByConsultation) },
        )
    }

    private fun ClinicalDocument.toSearchEntry(
        hprsByConsultation: Map<ConsultationId, List<String>>
    ): Bundle.Entry {
        val hprs =
            if (note.source == ClinicalNoteSource.EHR) {
                hprsByConsultation[note.consultationId].orEmpty()
            } else {
                authorHprs
            }
        val documentReference = toDocumentReference(hprs)
        return Bundle.Entry(
            fullUrl = Uri(value = "DocumentReference/${documentReference.id}"),
            resource = documentReference,
        )
    }
}
