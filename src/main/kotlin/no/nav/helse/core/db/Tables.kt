package no.nav.helse.core.db

import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.maaling.MaalingStatus
import no.nav.helse.epj.pasient.AdministrativeGender
import no.nav.helse.epj.pasient.PersonidentType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

object PasientTable : Table("patient") {
    val id = uuid("id")
    val fornavn = text("given_name")
    val etternavn = text("family_name")
    val personident = text("personident")
    val personidentType =
        enumerationByName<PersonidentType>("personident_type", length = 3).nullable()
    val birthDate = date("birth_date").nullable()
    val gender = enumerationByName<AdministrativeGender>("gender", length = 7).nullable()
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}

object ClinicTable : Table("clinic") {
    val id = uuid("id")
    val name = text("name")
    val phone = text("phone")
    val orgNumber = text("org_number")
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}

object HelsepersonellTable : Table("practitioner") {
    val id = uuid("id")
    val hpr = text("hpr")
    val navn = text("name")
    val autorisasjon = text("qualification")
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}

object KonsultasjonTable : Table("consultation") {
    val id = uuid("id")
    val pasientId = reference("patient_id", refColumn = PasientTable.id)
    val clinicId = reference("clinic_id", refColumn = ClinicTable.id)
    val startetTidspunkt = datetime("started_at")
    val avsluttetTidspunkt = datetime("ended_at")
    val status =
        varchar("status", 20).transform(KonsultasjonStatus::fromLabel, KonsultasjonStatus::label)
    val problemstilling = text("reason_for_visit").nullable()
    val created = datetime("created_at")
    val updated = datetime("updated_at")

    init {
        index(isUnique = true, id)
    }
}

object JournalnotatTable : Table("clinical_note") {
    val id = uuid("id")
    val konsultasjonId = reference("consultation_id", refColumn = KonsultasjonTable.id)
    val pasientId = reference("patient_id", refColumn = PasientTable.id)
    val journalnotat = text("clinical_note").nullable()

    init {
        index(isUnique = true, id)
    }
}

object KonsultasjonDiagnosekodeTable : Table("consultation_diagnosis_code") {
    val konsultasjonId = reference("consultation_id", refColumn = KonsultasjonTable.id)
    val diagnosesystem = text("diagnosis_system")
    val diagnosekode = text("diagnosis_code")

    override val primaryKey = PrimaryKey(konsultasjonId, diagnosesystem, diagnosekode)
}

object KonsultasjonHelsepersonell : Table("consultation_practitioner") {
    val konsultasjonId = reference("consultation_id", refColumn = KonsultasjonTable.id)
    val hpr = text("hpr")
}

object MaalingTable : Table("measurement") {
    val id = uuid("id")
    val pasientId = reference("patient_id", refColumn = PasientTable.id)
    val konsultasjonId = reference("consultation_id", refColumn = KonsultasjonTable.id)
    val hpr = text("hpr").nullable()
    val loincKode = text("loinc_code")
    val loincVisningsnavn = text("loinc_display")
    val verdi = decimal("value", precision = 12, scale = 4)
    val enhetKode = text("unit_code")
    val enhetVisningsnavn = text("unit_display")
    val effektivTidspunkt = datetime("effective_at")
    val status = enumerationByName<MaalingStatus>("status", length = 20)
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}

object FastlegelisteTable : Table("fastlegeliste") {
    val id = uuid("id")
    val hpr = text("hpr")
    val clinicId = reference("clinic_id", refColumn = ClinicTable.id)
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}

object FastlegelistePasientTable : Table("fastlegeliste_patient") {
    val fastlegelisteId = reference("fastlegeliste_id", refColumn = FastlegelisteTable.id)
    val pasientId = reference("patient_id", refColumn = PasientTable.id)
}
