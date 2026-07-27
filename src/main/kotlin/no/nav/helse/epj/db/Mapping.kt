package no.nav.helse.epj.db

import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.JournalnotatTable
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.epj.api.Diagnose
import no.nav.helse.epj.api.DiagnoseSystem
import no.nav.helse.epj.api.Journalnotat
import no.nav.helse.epj.api.Konsultasjon
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.ExperimentalUuidApi

fun ResultRow.toDiagnose() =
  Diagnose(
    kode = this[DiagnoseTable.diagnosekode],
    system = DiagnoseSystem.valueOf(this[DiagnoseTable.diagnosesystem]),
    beskrivelse = this[DiagnoseTable.beskrivelse],
  )

@OptIn(ExperimentalUuidApi::class)
fun ResultRow.toKonsultasjon(
  hprListe: List<String>,
  journalnotatListe: List<Journalnotat>,
): Konsultasjon =
  Konsultasjon(
    id = this[KonsultasjonTable.id].toString(),
    pasientId = this[KonsultasjonTable.pasientId].toString(),
    hpr = hprListe,
    startetTidspunkt = this[KonsultasjonTable.startetTidspunkt],
    avsluttetTidspunkt = this[KonsultasjonTable.avsluttetTidspunkt],
    status = this[KonsultasjonTable.status],
    problemstilling = this[KonsultasjonTable.problemstilling],
    journalnotat = journalnotatListe,
  )

@OptIn(ExperimentalUuidApi::class)
fun ResultRow.toJournalnotat(): Journalnotat =
  Journalnotat(
    id = this[JournalnotatTable.id].toString(),
    konsultasjonId = this[JournalnotatTable.konsultasjonId].toString(),
    pasientId = this[JournalnotatTable.pasientId].toString(),
    journalnotat = this[JournalnotatTable.journalnotat],
  )
