package no.nav.helse.epj.konsultasjon

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PatientId

@JvmInline @Serializable value class KonsultasjonId(val value: Uuid)

@Serializable
data class Konsultasjon(
  val id: KonsultasjonId,
  val pasientId: PatientId,
  val hpr: List<String>,
  val journalnotat: List<Journalnotat>,
  val diagnoser: List<Diagnose>,
  val startetTidspunkt: LocalDateTime,
  val avsluttetTidspunkt: LocalDateTime?,
  val status: KonsultasjonStatus,
  val problemstilling: String?,
)

@Serializable
data class Journalnotat(
  val id: PatientId,
  val konsultasjonId: KonsultasjonId,
  val pasientId: PatientId,
  val journalnotat: String?,
)

data class OpprettKonsultasjon(
  val pasientId: PatientId,
  val hpr: List<HelsepersonellHpr>,
  val startetTidspunkt: LocalDateTime,
  val status: KonsultasjonStatus,
)

@Serializable
data class OppdaterKonsultasjonRequest(
  val konsultasjonId: KonsultasjonId,
  val diagnoser: List<OpprettDiagnoseRequest>,
  val journalNotat: String?,
  val ferdigstill: Boolean,
)

@Serializable
data class OpprettDiagnoseRequest(
  val kode: String,
  val system: DiagnoseSystem,
  val beskrivelse: String,
)

@Serializable data class DiagnoseId(val value: Uuid)

@Serializable
data class Diagnose(
  val id: DiagnoseId,
  val patientId: PatientId,
  val kode: String,
  val system: DiagnoseSystem,
  val beskrivelse: String,
)

enum class DiagnoseSystem {
  ICPC2,
  ICD10,
}
