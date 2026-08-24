package no.nav.helse.core.db

import no.nav.helse.core.utils.KonsultasjonStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object PasientTable : Table("pasient") {
  val id = uuid("id")
  val legekontorId = reference("legekontor_id", refColumn = LegekontorTable.id)
  val navn = text("navn")
  val fnr = text("fnr")
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

object LegekontorTable : Table("legekontor") {
  val id = uuid("id")
  val navn = text("navn")
  val tlf = text("tlf")
  val orgnummer = text("orgnummer")
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

object HelsepersonellTable : Table("helsepersonell") {
  val id = uuid("id")
  val legekontorId = reference("legekontor_id", refColumn = LegekontorTable.id)
  val hpr = text("hpr")
  val navn = text("navn")
  val autorisasjon = text("autorisasjon")
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

object KonsultasjonTable : Table("konsultasjon") {
  val id = uuid("id")
  val pasientId = reference("pasient_id", refColumn = PasientTable.id)
  val startetTidspunkt = datetime("startet_tidspunkt")
  val avsluttetTidspunkt = datetime("avsluttet_tidspunkt")
  val status = enumerationByName<KonsultasjonStatus>("status", 20)
  val problemstilling = text("problemstilling").nullable()
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

object JournalnotatTable : Table("journalnotat") {
  val id = uuid("id")
  val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
  val pasientId = reference("pasient_id", refColumn = PasientTable.id)
  val journalnotat = text("journalnotat").nullable()
}

object DiagnoseTable : Table("diagnose") {
  val id = uuid("id")
  val patientId = reference("patient_id", refColumn = PasientTable.id)
  val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
  val diagnosekode = text("diagnosekode")
  val diagnosesystem = text("diagnosesystem")
  val beskrivelse = text("beskrivelse")

  init {
    uniqueIndex(
      "diagnose_konsultasjon_system_kode_unique",
      konsultasjonId,
      diagnosekode,
      diagnosesystem,
    )
  }
}

object KonsultasjonHelsepersonell : Table("konsultasjon_helsepersonell") {
  val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
  val hpr = text("hpr")
}

object PasientHelsepersonell : Table("pasient_helsepersonell") {
  val pasientId = reference("pasient_id", refColumn = PasientTable.id)
  val hpr = text("hpr")
}
