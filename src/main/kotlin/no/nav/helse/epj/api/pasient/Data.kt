package no.nav.helse.epj.api.pasient

data class Pasient(
  val id: String,
  val legekontorId: String,
  val fastlegeId: String,
  val navn: String,
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
