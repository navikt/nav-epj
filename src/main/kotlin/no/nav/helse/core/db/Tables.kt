package no.nav.helse.core.db

import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.maaling.MaalingStatus
import no.nav.helse.epj.pasient.AdministrativeGender
import no.nav.helse.epj.pasient.PersonidentType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

object PasientTable : Table("pasient") {
    val id = uuid("id")
    val legekontorId = reference("legekontor_id", refColumn = LegekontorTable.id)
    val fornavn = text("fornavn")
    val etternavn = text("etternavn")
    val personident = text("personident")
    val personidentType =
        enumerationByName<PersonidentType>("personident_type", length = 3).nullable()
    val birthDate = date("birth_date").nullable()
    val gender = enumerationByName<AdministrativeGender>("gender", length = 7).nullable()
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
    val legekontorId = reference("legekontor_id", refColumn = LegekontorTable.id)
    val startetTidspunkt = datetime("startet_tidspunkt")
    val avsluttetTidspunkt = datetime("avsluttet_tidspunkt")
    val status =
        varchar("status", 20).transform(KonsultasjonStatus::fromLabel, KonsultasjonStatus::label)
    val problemstilling = text("problemstilling").nullable()
    val created = datetime("created_at")
    val updated = datetime("updated_at")

    init {
        index(isUnique = true, id)
    }
}

object JournalnotatTable : Table("journalnotat") {
    val id = uuid("id")
    val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
    val pasientId = reference("pasient_id", refColumn = PasientTable.id)
    val journalnotat = text("journalnotat").nullable()

    init {
        index(isUnique = true, id)
    }
}

object KonsultasjonDiagnosekodeTable : Table("konsultasjon_diagnosekode") {
    val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
    val diagnosesystem = text("diagnosesystem")
    val diagnosekode = text("diagnosekode")

    override val primaryKey = PrimaryKey(konsultasjonId, diagnosesystem, diagnosekode)
}

object KonsultasjonHelsepersonell : Table("konsultasjon_helsepersonell") {
    val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
    val hpr = text("hpr")
}

object PasientHelsepersonell : Table("pasient_helsepersonell") {
    val pasientId = reference("pasient_id", refColumn = PasientTable.id)
    val hpr = text("hpr")
}

object MaalingTable : Table("maaling") {
    val id = uuid("id")
    val pasientId = reference("pasient_id", refColumn = PasientTable.id)
    val konsultasjonId = reference("konsultasjon_id", refColumn = KonsultasjonTable.id)
    val hpr = text("hpr").nullable()
    val loincKode = text("loinc_kode")
    val loincVisningsnavn = text("loinc_visningsnavn")
    val verdi = decimal("verdi", precision = 12, scale = 4)
    val enhetKode = text("enhet_kode")
    val enhetVisningsnavn = text("enhet_visningsnavn")
    val effektivTidspunkt = datetime("effektiv_tidspunkt")
    val status = enumerationByName<MaalingStatus>("status", length = 20)
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}
