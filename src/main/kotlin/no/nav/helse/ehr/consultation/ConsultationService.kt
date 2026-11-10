package no.nav.helse.ehr.consultation

import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationNotFoundForPatientException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.logger
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr

class ConsultationService(private val consultationRepository: ConsultationRepository) {
    val log = logger()

    suspend fun getConsultations(patientId: PatientId, orgNumber: String?): List<Consultation> {
        return consultationRepository.listByPatientId(patientId, orgNumber)
    }

    suspend fun getActiveConsultation(patientId: PatientId, orgNumber: String?): Consultation? {
        return consultationRepository.findActiveByPatientId(patientId, orgNumber)
    }

    suspend fun getActiveConsultation(
        patientId: PatientId,
        hpr: PractitionerHpr,
        orgNumber: String?,
    ): Consultation? {
        return consultationRepository.findActiveByPatientIdAndHpr(patientId, hpr, orgNumber)
    }

    suspend fun getConsultation(consultationId: ConsultationId, orgNumber: String?): Consultation {
        return consultationRepository.findByConsultationId(consultationId, orgNumber)
            ?: throw ConsultationNotFoundException(consultationId)
    }

    suspend fun getClinicalNote(clinicalNoteId: ClinicalNoteId, orgNumber: String?): ClinicalNote? {
        return consultationRepository.findClinicalNote(clinicalNoteId, orgNumber)
    }

    suspend fun getClinicalNoteAttachment(
        clinicalNoteId: ClinicalNoteId,
        orgNumber: String?,
    ): ClinicalNoteAttachment? =
        consultationRepository.findClinicalNoteAttachment(clinicalNoteId, orgNumber)

    suspend fun insertClinicalNote(clinicalNote: ClinicalNote, orgNumber: String): Boolean {
        val insertedRows = consultationRepository.insertClinicalNote(clinicalNote, orgNumber)

        return insertedRows == 1
    }

    suspend fun getClinicalNotes(
        patientId: PatientId,
        consultationId: ConsultationId?,
        orgNumber: String?,
    ): List<ClinicalNote> {
        return consultationRepository.listClinicalNotes(patientId, consultationId, orgNumber)
    }

    @OptIn(ExperimentalUuidApi::class)
    /** [orgNumber] is null for a system token, which is not tied to one clinic. */
    suspend fun createClinicalNote(
        request: CreateClinicalNoteRequest,
        orgNumber: String?,
    ): ClinicalNote {
        val id = ClinicalNoteId(Uuid.generateV4())
        return consultationRepository.createClinicalNote(id, request, orgNumber)
    }

    suspend fun createConsultation(request: CreateConsultation): Consultation {
        val createdId = consultationRepository.insert(request)
        val createdConsultation =
            consultationRepository.findByConsultationId(createdId, request.orgNumber)
                ?: throw IllegalStateException("Konsultasjon ble ikke opprettet")

        log.info(
            "Created konsultasjon id={} for patientId={}",
            createdConsultation.id,
            request.patientId,
        )
        return createdConsultation
    }

    suspend fun getOrCreateConsultation(
        patientId: PatientId,
        hpr: PractitionerHpr,
        orgNumber: String,
    ): Consultation {
        val activeConsultation = getActiveConsultation(patientId, hpr, orgNumber)
        if (activeConsultation != null) return activeConsultation
        val request =
            CreateConsultation(
                patientId = patientId,
                hpr = listOf(hpr),
                startedAt = LocalDateTime.now(),
                status = ConsultationStatus.IN_PROGRESS,
                orgNumber = orgNumber,
            )
        return createConsultation(request)
    }

    suspend fun updateConsultation(
        updateConsultation: UpdateConsultationRequest,
        patientId: PatientId,
        orgNumber: String?,
    ) {
        log.info("oppdater konsultasjon på pasientId: $patientId")
        val updatedRows = consultationRepository.update(updateConsultation, patientId, orgNumber)
        if (updatedRows == 0) {
            throw ConsultationNotFoundForPatientException(patientId)
        }
    }

    suspend fun cancelConsultation(
        consultationId: ConsultationId,
        patientId: PatientId,
        orgNumber: String?,
    ) {
        log.info("avbryter konsultasjon {} for pasientId: {}", consultationId, patientId)
        val updatedRows = consultationRepository.cancel(consultationId, patientId, orgNumber)
        if (updatedRows == 0) {
            throw ConsultationNotFoundForPatientException(patientId)
        }
    }
}
