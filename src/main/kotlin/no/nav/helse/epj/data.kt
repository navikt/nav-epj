package no.nav.helse.epj

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
data class Pasient(val id: UUID, val legekontorId: UUID, val fastlegeId: UUID, val navn: String)

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
