package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType

@JvmInline value class KonsultasjonId(val value: Uuid)

@JvmInline value class JournalnotatId(val value: Uuid)

data class Konsultasjon(
    val id: KonsultasjonId,
    val patientId: PatientId,
    val clinicId: ClinicId,
    val hpr: List<String>,
    val journalnotat: List<Journalnotat>,
    val diagnoser: List<Diagnose>,
    val startetTidspunkt: LocalDateTime,
    val avsluttetTidspunkt: LocalDateTime?,
    val status: KonsultasjonStatus,
    val problemstilling: String?,
)

data class Journalnotat(
    val id: JournalnotatId,
    val konsultasjonId: KonsultasjonId,
    val patientId: PatientId,
    val journalnotat: String?,
)

data class OpprettJournalnotatRequest(
    val patientId: PatientId,
    val konsultasjonId: KonsultasjonId,
    val journalnotat: String,
)

data class OpprettKonsultasjon(
    val patientId: PatientId,
    val hpr: List<PractitionerHpr>,
    val startetTidspunkt: LocalDateTime,
    val status: KonsultasjonStatus,
    val orgNumber: String,
)

data class OppdaterKonsultasjonRequest(
    val konsultasjonId: KonsultasjonId,
    val diagnoser: List<OpprettDiagnoseRequest>,
    val journalNotat: String?,
    val ferdigstill: Boolean,
)

data class OpprettDiagnoseRequest(val kode: String, val system: DiagnoseType)
