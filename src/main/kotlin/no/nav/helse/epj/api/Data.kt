package no.nav.helse.epj.api

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Pasient(
  val id: String,
  val legekontorId: String,
  val fastlegeId: String,
  val navn: String,
  val fnr: String,
)

@Serializable data class OpprettPasientRequest(val navn: String, val fnr: String)

data class Legekontor(val id: String, val navn: String, val orgnummer: String?, val tlf: String?)

@Serializable
data class Helsepersonell(
  val id: String,
  val legekontorId: String,
  val hpr: String,
  val navn: String,
  val autorisasjon: String,
)

data class OpprettHelsepersonell(
  val legekontorId: String,
  val hpr: String,
  val navn: String,
  val autorisasjon: String,
)

enum class KonsultasjonStatus {
  PLANLAGT,
  PÅGÅENDE,
  FULLFØRT,
  AVLYST,
}

@Serializable
data class Konsultasjon(
  val id: String,
  val pasientId: String,
  val hpr: List<String>,
  val startetTidspunkt: LocalDateTime,
  val avsluttetTidspunkt: LocalDateTime?,
  val status: KonsultasjonStatus,
  val problemstilling: String?,
  val journalnotat: String?,
)

data class OpprettKonsultasjon(
  val pasientId: String,
  val hpr: List<String>,
  val startetTidspunkt: LocalDateTime,
  val status: KonsultasjonStatus,
)

@Serializable
data class OppdaterKonsultasjonRequest(
  val konsultasjonId: String,
  val diagnoser: List<OpprettDiagnoseRequest>,
  val journalNotat: String?,
  val ferdigstill: Boolean,
)

@Serializable
data class OpprettDiagnoseRequest(val kode: String, val system: String, val beskrivelse: String)

data class Diagnose(val kode: String, val system: String, val beskrivelse: String)
