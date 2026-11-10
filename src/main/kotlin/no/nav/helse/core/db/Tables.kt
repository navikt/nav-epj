package no.nav.helse.core.db

import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.measurement.MeasurementStatus
import no.nav.helse.ehr.patient.AdministrativeGender
import no.nav.helse.ehr.patient.PersonidentType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

object PatientTable : Table("patient") {
    val id = uuid("id")
    val givenName = text("given_name")
    val familyName = text("family_name")
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

object PractitionerTable : Table("practitioner") {
    val id = uuid("id")
    val hpr = text("hpr")
    val name = text("name")
    val qualification = text("qualification")
    val created = datetime("created_at")
    val updated = datetime("updated_at")
}

object ConsultationTable : Table("consultation") {
    val id = uuid("id")
    val patientId = reference("patient_id", refColumn = PatientTable.id)
    val clinicId = reference("clinic_id", refColumn = ClinicTable.id)
    val startedAt = datetime("started_at")
    val endedAt = datetime("ended_at")
    val status =
        varchar("status", 20).transform(ConsultationStatus::fromLabel, ConsultationStatus::label)
    val reasonForVisit = text("reason_for_visit").nullable()
    val created = datetime("created_at")
    val updated = datetime("updated_at")

    init {
        index(isUnique = true, id)
    }
}

object ClinicalNoteTable : Table("clinical_note") {
    val id = uuid("id")
    val consultationId = reference("consultation_id", refColumn = ConsultationTable.id)
    val patientId = reference("patient_id", refColumn = PatientTable.id)
    val clinicalNote = text("clinical_note").nullable()
    val attachmentTitle = text("attachment_title").nullable()
    val attachmentData = binary("attachment_data").nullable()

    init {
        index(isUnique = true, id)
    }
}

object ConsultationDiagnosisCodeTable : Table("consultation_diagnosis_code") {
    val consultationId = reference("consultation_id", refColumn = ConsultationTable.id)
    val diagnosisSystem = text("diagnosis_system")
    val diagnosisCode = text("diagnosis_code")
    val rank = short("rank")

    override val primaryKey = PrimaryKey(consultationId, diagnosisSystem, diagnosisCode)
}

object ConsultationPractitioner : Table("consultation_practitioner") {
    val consultationId = reference("consultation_id", refColumn = ConsultationTable.id)
    val hpr = text("hpr")
}

object MeasurementTable : Table("measurement") {
    val id = uuid("id")
    val patientId = reference("patient_id", refColumn = PatientTable.id)
    val consultationId = reference("consultation_id", refColumn = ConsultationTable.id)
    val hpr = text("hpr").nullable()
    val loincCode = text("loinc_code")
    val loincDisplay = text("loinc_display")
    val value = decimal("value", precision = 12, scale = 4)
    val unitCode = text("unit_code")
    val unitDisplay = text("unit_display")
    val effectiveAt = datetime("effective_at")
    val status = enumerationByName<MeasurementStatus>("status", length = 20)
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

object FastlegelistePatientTable : Table("fastlegeliste_patient") {
    val fastlegelisteId = reference("fastlegeliste_id", refColumn = FastlegelisteTable.id)
    val patientId = reference("patient_id", refColumn = PatientTable.id)
}
