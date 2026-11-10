package no.nav.helse.ehr.consultation

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

data class ClinicalNote(
    val id: ClinicalNoteId,
    val consultationId: ConsultationId,
    val patientId: PatientId,
    val clinicalNote: String?,
    /** Title of the document attached through FHIR, or null for a note without one. */
    val attachmentTitle: String? = null,
    val hasAttachment: Boolean = false,
)

/** A document attached to a clinical note through FHIR. Only PDF is supported. */
class ClinicalNoteAttachment(val title: String, val data: ByteArray)

data class CreateClinicalNoteRequest(
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val clinicalNote: String,
    val attachment: ClinicalNoteAttachment? = null,
)

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
