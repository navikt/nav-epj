package no.nav.helse.epj.patient

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicTable
import no.nav.helse.core.db.ConsultationTable
import no.nav.helse.core.db.FastlegelistePatientTable
import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.PatientTable
import no.nav.helse.core.db.consultationAtOrg
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.DuplicatePatientException
import no.nav.helse.core.utils.IngenFastlegelisteException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.practitioner.PractitionerHpr
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class PatientRepository {

    private val logger = logger()

    suspend fun findById(id: Uuid) = dbQuery {
        logger.info("Looking up patient by id: $id")
        val patient = PatientTable.selectAll().where { PatientTable.id eq id }.singleOrNull()
        patient?.toPatient()
    }

    suspend fun findByPersonident(personident: String) = dbQuery {
        logger.info("Looking up patient by personident")
        val patient =
            PatientTable.selectAll()
                .where { PatientTable.personident eq personident }
                .singleOrNull()
        patient?.toPatient()
    }

    suspend fun listAtClinic(orgNumber: String, ownHpr: PractitionerHpr): List<PatientListItem> =
        dbQuery {
            val rows =
                FastlegelistePatientTable.join(
                        FastlegelisteTable,
                        JoinType.INNER,
                        FastlegelistePatientTable.fastlegelisteId,
                        FastlegelisteTable.id,
                    )
                    .join(ClinicTable, JoinType.INNER, FastlegelisteTable.clinicId, ClinicTable.id)
                    .join(
                        PatientTable,
                        JoinType.INNER,
                        FastlegelistePatientTable.patientId,
                        PatientTable.id,
                    )
                    .selectAll()
                    .where { ClinicTable.orgNumber eq orgNumber }
                    .toList()

            val listedIds = rows.map { it[PatientTable.id] }
            val lapsedRows =
                PatientTable.selectAll()
                    .where {
                        (PatientTable.id inSubQuery consultationPatientIdsAtOrg(orgNumber)) and
                            (PatientTable.id notInList listedIds)
                    }
                    .toList()

            val latestConsultation = latestConsultationByPatient(orgNumber)

            val listed =
                rows
                    .sortedWith(
                        compareBy<ResultRow> { it[FastlegelisteTable.hpr] != ownHpr.value }
                            .thenBy { it[PatientTable.familyName] }
                            .thenBy { it[PatientTable.givenName] }
                    )
                    .map {
                        it.toPatient().let { patient ->
                            PatientListItem(
                                patient,
                                selectable = true,
                                latestConsultation = latestConsultation[patient.id.value],
                            )
                        }
                    }
            val lapsed =
                lapsedRows
                    .sortedWith(
                        compareBy<ResultRow> { it[PatientTable.familyName] }
                            .thenBy { it[PatientTable.givenName] }
                    )
                    .map {
                        it.toPatient().let { patient ->
                            PatientListItem(
                                patient,
                                selectable = false,
                                latestConsultation = latestConsultation[patient.id.value],
                            )
                        }
                    }
            listed + lapsed
        }

    private fun latestConsultationByPatient(orgNumber: String): Map<Uuid, LatestConsultation> =
        ConsultationTable.select(
                ConsultationTable.patientId,
                ConsultationTable.status,
                ConsultationTable.startedAt,
                ConsultationTable.endedAt,
            )
            .where { consultationAtOrg(orgNumber) }
            .orderBy(ConsultationTable.startedAt, SortOrder.DESC)
            .groupBy { it[ConsultationTable.patientId] }
            .mapValues { (_, consultations) ->
                val siste = consultations.first()
                val avsluttet: LocalDateTime? = siste[ConsultationTable.endedAt]
                LatestConsultation(
                    status = siste[ConsultationTable.status],
                    timestamp = avsluttet ?: siste[ConsultationTable.startedAt],
                )
            }

    suspend fun hasConsultationAt(patientId: Uuid, orgNumber: String): Boolean = dbQuery {
        ConsultationTable.select(ConsultationTable.id)
            .where { (ConsultationTable.patientId eq patientId) and consultationAtOrg(orgNumber) }
            .any()
    }

    private fun consultationPatientIdsAtOrg(orgNumber: String) =
        ConsultationTable.select(ConsultationTable.patientId).where { consultationAtOrg(orgNumber) }

    suspend fun isListedAt(patientId: Uuid, orgNumber: String): Boolean = dbQuery {
        FastlegelistePatientTable.join(
                FastlegelisteTable,
                JoinType.INNER,
                FastlegelistePatientTable.fastlegelisteId,
                FastlegelisteTable.id,
            )
            .join(ClinicTable, JoinType.INNER, FastlegelisteTable.clinicId, ClinicTable.id)
            .select(FastlegelistePatientTable.patientId)
            .where {
                (FastlegelistePatientTable.patientId eq patientId) and
                    (ClinicTable.orgNumber eq orgNumber)
            }
            .any()
    }

    suspend fun insertOnList(patient: Patient, listHpr: PractitionerHpr, orgNumber: String): Unit =
        dbQuery {
            val listId =
                FastlegelisteTable.join(
                        ClinicTable,
                        JoinType.INNER,
                        FastlegelisteTable.clinicId,
                        ClinicTable.id,
                    )
                    .select(FastlegelisteTable.id)
                    .where {
                        (FastlegelisteTable.hpr eq listHpr.value) and
                            (ClinicTable.orgNumber eq orgNumber)
                    }
                    .singleOrNull()
                    ?.get(FastlegelisteTable.id) ?: throw IngenFastlegelisteException()

            insertPatient(patient)
            FastlegelistePatientTable.insert {
                it[fastlegelisteId] = listId
                it[patientId] = patient.id.value
            }
            Unit
        }

    suspend fun insert(patient: Patient) = dbQuery { insertPatient(patient) }

    private fun insertPatient(patient: Patient) {
        logger.info("Inserting patient with id: ${patient.id.value}")

        val insertStatement = PatientTable.insertIgnore {
            it[id] = patient.id.value
            it[givenName] = patient.givenName
            it[familyName] = patient.familyName
            it[personident] = patient.personident
            it[personidentType] = patient.personidentType
            it[birthDate] = patient.birthDate
            it[gender] = patient.gender
        }

        if (insertStatement.insertedCount == 0) {
            throw DuplicatePatientException()
        }
    }

    private fun ResultRow.toPatient(): Patient =
        Patient(
            id = PatientId(this[PatientTable.id]),
            givenName = this[PatientTable.givenName],
            familyName = this[PatientTable.familyName],
            personident = this[PatientTable.personident],
            personidentType = this[PatientTable.personidentType],
            birthDate = this[PatientTable.birthDate],
            gender = this[PatientTable.gender],
        )
}
