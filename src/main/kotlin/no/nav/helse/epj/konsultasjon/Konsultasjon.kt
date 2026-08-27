package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PasientId

@JvmInline value class KonsultasjonId(val value: Uuid)

@JvmInline value class JournalnotatId(val value: Uuid)

data class Konsultasjon(
  val id: KonsultasjonId,
  val pasientId: PasientId,
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
  val pasientId: PasientId,
  val journalnotat: String?,
)

data class OpprettKonsultasjon(
  val pasientId: PasientId,
  val hpr: List<HelsepersonellHpr>,
  val startetTidspunkt: LocalDateTime,
  val status: KonsultasjonStatus,
)

data class OppdaterKonsultasjonRequest(
  val konsultasjonId: KonsultasjonId,
  val diagnoser: List<OpprettDiagnoseRequest>,
  val journalNotat: String?,
  val ferdigstill: Boolean,
)

data class OpprettDiagnoseRequest(
  val kode: String,
  val system: DiagnoseSystem,
  val beskrivelse: String,
)

@JvmInline value class DiagnoseId(val value: Uuid)

data class Diagnose(
  val id: DiagnoseId,
  val pasientId: PasientId,
  val kode: String,
  val system: DiagnoseSystem,
  val beskrivelse: String,
)

enum class DiagnoseSystem {
  ICPC2,
  ICD10,
}
