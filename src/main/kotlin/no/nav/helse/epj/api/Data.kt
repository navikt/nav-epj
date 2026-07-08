package no.nav.helse.epj.api

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Pasient(
  val id: String,
  val legekontorId: String,
  val fastlegeId: String,
  val navn: String,
)

@Serializable
data class Helsepersonell(
  val id: String,
  val legekontorId: String,
  val hpr: String,
  val herId: String?,
  val navn: String,
  val autorisasjon: String,
)

data class OpprettHelsepersonell(
  val legekontorId: String,
  val hpr: String,
  val navn: String,
  val autorisasjon: String,
)

@Serializable
data class Konsultasjon(
  val id: String,
  val pasientId: String,
  val hpr: List<String>,
  val startetTidspunkt: LocalDateTime,
  val avsluttetTidspunkt: LocalDateTime?,
  val type: String, // -- fysisk, video, telefon
  val status: String, // -- planlagt, pågående, fullført, avlyst
  val problemstilling: String?,
  val journalnotat: String?,
)

data class OpprettKonsultasjon(
  val pasientId: String,
  val hpr: List<String>,
  val startetTidspunkt: LocalDateTime,
  val type: String,
  val status: String,
)

@Serializable
data class OppdaterKonsultasjonRequest(
  val konsultasjonId: String,
  val diagnoser: List<OpprettDiagnoseRequest>,
  val journalNotat: String?,
  val ferdigstill: Boolean,
)

@Serializable data class OpprettDiagnoseRequest(val kode: String, val system: String)
