package no.nav.helse.epj.consultation

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType

@JvmInline value class ConsultationId(val value: Uuid)

@JvmInline value class JournalnotatId(val value: Uuid)

data class Consultation(
    val id: ConsultationId,
    val patientId: PatientId,
    val clinicId: ClinicId,
    val hpr: List<String>,
    val journalnotat: List<Journalnotat>,
    val diagnoses: List<Diagnose>,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val status: ConsultationStatus,
    val reasonForVisit: String?,
)

data class Journalnotat(
    val id: JournalnotatId,
    val consultationId: ConsultationId,
    val patientId: PatientId,
    val journalnotat: String?,
)

data class OpprettJournalnotatRequest(
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val journalnotat: String,
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
    val journalNotat: String?,
    val finalize: Boolean,
)

data class CreateDiagnosisRequest(val code: String, val system: DiagnoseType)
