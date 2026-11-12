package no.nav.helse.ehr.consultation

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType

@JvmInline value class ConsultationId(val value: Uuid)

@JvmInline value class ClinicalNoteId(val value: Uuid)

data class Consultation(
    val id: ConsultationId,
    val patientId: PatientId,
    val clinicId: ClinicId,
    val hpr: List<String>,
    val clinicalNotes: List<ClinicalNote>,
    val diagnoses: List<Diagnose>,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val status: ConsultationStatus,
    val reasonForVisit: String?,
)

enum class ClinicalNoteSource {
    EHR,
    FHIR,
}

data class ClinicalNote(
    val id: ClinicalNoteId,
    val consultationId: ConsultationId,
    val patientId: PatientId,
    val clinicalNote: String?,
    val source: ClinicalNoteSource = ClinicalNoteSource.EHR,
    val attachmentTitle: String? = null,
    val hasAttachment: Boolean = false,
    val attachmentExpired: Boolean = false,
)

class ClinicalNoteAttachment(val title: String?, val data: ByteArray)

class StoredAttachment(
    val title: String?,
    val language: String?,
    val contentType: String,
    val expired: Boolean,
    val data: ByteArray?,
)

class ClinicalDocument(
    val note: ClinicalNote,
    val typeText: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val relatedQuestionnaireResponseId: Uuid?,
    val authorHprs: List<String>,
    val attachment: StoredAttachment?,
)

class NewAttachment(val title: String?, val language: String?, val data: ByteArray)

class RetainedAttachment(val title: String?, val language: String?)

class ClinicalDocumentWrite(
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val description: String?,
    val typeText: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val relatedQuestionnaireResponseId: Uuid?,
    val authorHprs: List<String>,
    val attachment: NewAttachment?,
    val retainedAttachment: RetainedAttachment? = null,
)

data class ClinicalDocumentWriteResult(val id: ClinicalNoteId, val created: Boolean)

enum class ClinicalNoteDeleteResult {
    DELETED,
    NOT_FOUND,
    PROTECTED,
}

data class CreateConsultation(
    val patientId: PatientId,
    val hpr: List<PractitionerHpr>,
    val startedAt: LocalDateTime,
    val status: ConsultationStatus,
    val orgNumber: String,
)

data class UpdateConsultationRequest(
    val consultationId: ConsultationId,
    val diagnoses: List<CreateDiagnosisRequest>,
    val clinicalNote: String?,
    val finalize: Boolean,
)

data class CreateDiagnosisRequest(val code: String, val system: DiagnoseType)
