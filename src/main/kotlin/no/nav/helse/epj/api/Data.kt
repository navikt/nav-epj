package no.nav.helse.epj.api

import kotlinx.datetime.LocalDateTime

data class Pasient(
  val id: String,
  val legekontorId: String,
  val fastlegeId: String,
  val navn: String,
)

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

data class Konsultasjon(
  val id: String,
  val pasientId: String,
  val helsepersonellId: String,
  val startetTidspunkt: LocalDateTime,
  val avsluttetTidspunkt: LocalDateTime?,
  val type: String,
  val status: String,
  val problemstilling: String,
  val journalnotat: String,
)

/**
 * vise liste over pasienter: hente pasient i db lage api for dette starte konsultasjon på pasient:
 * opprette konsultasjon og lagre i db - får alt vi trenger fra frontend
 */

// laget api for konsultasjon
// hente ut og
// pasienter
// pasient på id
// helsepersonell
// legekontor
