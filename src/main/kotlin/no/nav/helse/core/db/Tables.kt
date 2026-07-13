package no.nav.helse.core.db

import kotlin.uuid.ExperimentalUuidApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

@OptIn(ExperimentalUuidApi::class)
object PasientTable : Table("pasient") {
  val id = uuid("id")
  val legekontorId = reference("legekontor_id", refColumn = LegekontorTable.id)
  val fastlegeId = reference("fastlege", refColumn = HelsepersonellTable.id)
  val navn = text("navn")
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

@OptIn(ExperimentalUuidApi::class)
object LegekontorTable : Table("legekontor") {
  val id = uuid("id")
  val navn = text("navn")
  val tlf = text("tlf")
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

@OptIn(ExperimentalUuidApi::class)
object HelsepersonellTable : Table("helsepersonell") {
  val id = uuid("id")
  val legekontorId = reference("legekontor_id", refColumn = LegekontorTable.id)
  val hpr = text("hpr")
  val herId = text("her_id")
  val helseidSub = text("helseid_sub")
  val navn = text("navn")
  val autorisasjon = text("autorisasjon")
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

@OptIn(ExperimentalUuidApi::class)
object KonsultasjonTable : Table("konsultasjon") {
  val id = uuid("id")
  val pasientId = reference("pasient_id", refColumn = PasientTable.id)
  val startetTidspunkt = datetime("startet_tidspunkt")
  val avsluttetTidspunkt = datetime("avsluttet_tidspunkt")
  val type = text("type")
  val status = text("status")
  val problemstilling = text("problemstilling").nullable()
  val journalnotat = text("journalnotat").nullable()
  val created = datetime("created_at")
  val updated = datetime("updated_at")
}

@OptIn(ExperimentalUuidApi::class)
object DiagnoseTable : Table("diagnose") {
  val id = integer("id").autoIncrement()
  val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
  val diagnosekode = text("diagnosekode")
  val diagnosesystem = text("diagnosesystem")
  val beskrivelse = text("beskrivelse")
}

@OptIn(ExperimentalUuidApi::class)
object KonsultasjonHelsepersonell : Table("konsultasjon_helsepersonell") {
  val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
  val hpr = text("hpr")
}
