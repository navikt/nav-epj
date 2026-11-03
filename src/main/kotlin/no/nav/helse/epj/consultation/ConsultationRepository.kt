package no.nav.helse.epj.consultation

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicTable
import no.nav.helse.core.db.ConsultationDiagnosisCodeTable
import no.nav.helse.core.db.ConsultationPractitioner
import no.nav.helse.core.db.ConsultationTable
import no.nav.helse.core.db.JournalnotatTable
import no.nav.helse.core.db.consultationAtOrg
import no.nav.helse.core.db.consultationIdsAtOrg
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.ClinicNotFoundException
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.DuplikatJournalnotatException
import no.nav.helse.core.utils.InvalidDiagnosisException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.tsm.diagnoser.Diagnose
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

class ConsultationRepository {
    private val logger = logger()

    suspend fun listByPatientId(id: PatientId, orgNumber: String?): List<Consultation> = dbQuery {
        val consultations =
            ConsultationTable.selectAll()
                .where {
                    (ConsultationTable.patientId eq id.value) and consultationAtOrg(orgNumber)
                }
                .orderBy(ConsultationTable.startedAt, SortOrder.DESC)
                .toList()

        val consultationIds = consultations.map { it[ConsultationTable.id] }
        val hprByConsultationId =
            ConsultationPractitioner.selectAll()
                .where { ConsultationPractitioner.consultationId inList consultationIds }
                .groupBy(
                    keySelector = { it[ConsultationPractitioner.consultationId] },
                    valueTransform = { it[ConsultationPractitioner.hpr] },
                )

        val journalnotatByConsultationId =
            JournalnotatTable.selectAll()
                .where { JournalnotatTable.consultationId inList consultationIds }
                .groupBy(
                    keySelector = { it[JournalnotatTable.consultationId] },
                    valueTransform = { it.toJournalnotat() },
                )

        val diagnosesByConsultationId =
            ConsultationDiagnosisCodeTable.selectAll()
                .where { ConsultationDiagnosisCodeTable.consultationId inList consultationIds }
                .groupBy(
                    keySelector = { it[ConsultationDiagnosisCodeTable.consultationId] },
                    valueTransform = { it.toDiagnosis() },
                )

        consultations.map { row ->
            val consultationId = row[ConsultationTable.id]
            val hprListe = hprByConsultationId[consultationId].orEmpty()
            val journalnotatListe = journalnotatByConsultationId[consultationId].orEmpty()
            val diagnosisList = diagnosesByConsultationId[consultationId].orEmpty()
            row.toConsultationWithHprAndJournalnotat(hprListe, journalnotatListe, diagnosisList)
        }
    }

    suspend fun listDiagnoses(id: PatientId, orgNumber: String?): List<Diagnose> = dbQuery {
        val consultationIds =
            ConsultationTable.select(ConsultationTable.id)
                .where {
                    (ConsultationTable.patientId eq id.value) and consultationAtOrg(orgNumber)
                }
                .map { it[ConsultationTable.id] }

        if (consultationIds.isEmpty()) {
            return@dbQuery emptyList()
        }

        ConsultationDiagnosisCodeTable.selectAll()
            .where { ConsultationDiagnosisCodeTable.consultationId inList consultationIds }
            .map { it.toDiagnosis() }
    }

    suspend fun listDiagnoses(id: ConsultationId, orgNumber: String?): List<Diagnose> = dbQuery {
        ConsultationDiagnosisCodeTable.selectAll()
            .where {
                (ConsultationDiagnosisCodeTable.consultationId eq id.value) and
                    (ConsultationDiagnosisCodeTable.consultationId inSubQuery
                        consultationIdsAtOrg(orgNumber))
            }
            .map { it.toDiagnosis() }
    }

    suspend fun insert(createConsultation: CreateConsultation) = dbQuery {
        val clinicId =
            ClinicTable.select(ClinicTable.id)
                .where { ClinicTable.orgNumber eq createConsultation.orgNumber }
                .singleOrNull()
                ?.get(ClinicTable.id) ?: throw ClinicNotFoundException(createConsultation.orgNumber)
        val consultation =
            ConsultationTable.insertReturning {
                    it[patientId] = createConsultation.patientId.value
                    it[ConsultationTable.clinicId] = clinicId
                    it[startedAt] = createConsultation.startedAt
                    it[status] = createConsultation.status
                }
                .single()
        val id = consultation[ConsultationTable.id]
        createConsultation.hpr.forEach { hprValue ->
            ConsultationPractitioner.insert {
                it[consultationId] = id
                it[hpr] = hprValue.value
            }
        }
        ConsultationId(consultation[ConsultationTable.id])
    }

    suspend fun findActiveByPatientId(patientId: PatientId, orgNumber: String?): Consultation? {
        val patientUuid = patientId.value
        return dbQuery {
            val consultation =
                ConsultationTable.selectAll()
                    .where {
                        (ConsultationTable.patientId eq patientUuid) and
                            (ConsultationTable.status eq ConsultationStatus.IN_PROGRESS) and
                            consultationAtOrg(orgNumber)
                    }
                    .orderBy(ConsultationTable.startedAt, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull() ?: return@dbQuery null
            toEpjConsultation(consultation)
        }
    }

    suspend fun findActiveByPatientIdAndHpr(
        patientId: PatientId,
        hpr: PractitionerHpr,
        orgNumber: String?,
    ): Consultation? {
        val patientUuid = patientId.value
        return dbQuery {
            val consultation =
                (ConsultationTable innerJoin ConsultationPractitioner)
                    .selectAll()
                    .where {
                        (ConsultationTable.patientId eq patientUuid) and
                            (ConsultationTable.status eq ConsultationStatus.IN_PROGRESS) and
                            (ConsultationPractitioner.hpr eq hpr.value) and
                            consultationAtOrg(orgNumber)
                    }
                    .orderBy(ConsultationTable.startedAt, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull() ?: return@dbQuery null
            toEpjConsultation(consultation)
        }
    }

    suspend fun findByConsultationId(
        consultationId: ConsultationId,
        orgNumber: String?,
    ): Consultation? {
        return dbQuery {
            val consultation =
                ConsultationTable.selectAll()
                    .where {
                        (ConsultationTable.id eq consultationId.value) and
                            consultationAtOrg(orgNumber)
                    }
                    .singleOrNull() ?: return@dbQuery null
            toEpjConsultation(consultation)
        }
    }

    suspend fun update(
        updateConsultation: UpdateConsultationRequest,
        patientId: PatientId,
        orgNumber: String?,
    ): Int = dbQuery {
        logger.info("Oppdaterer konsultasjon {}", updateConsultation.consultationId)

        val consultationPatient =
            ConsultationTable.selectAll()
                .where {
                    (ConsultationTable.id eq updateConsultation.consultationId.value) and
                        (ConsultationTable.patientId eq patientId.value) and
                        consultationAtOrg(orgNumber)
                }
                .limit(1)
                .any()

        if (!consultationPatient) {
            logger.warn(
                "Fant ikke konsultasjon {} for pasientId {}, avbryter oppdatering",
                updateConsultation.consultationId,
                patientId,
            )
            return@dbQuery 0
        }

        var updatedRows = 0

        updatedRows +=
            removeDiagnosesNotIn(
                diagnoses = updateConsultation.diagnoses,
                consultationId = updateConsultation.consultationId.value,
            )

        updateConsultation.diagnoses.forEach { diagnosis ->
            updatedRows +=
                updateDiagnosis(
                    diagnosis = diagnosis,
                    consultationId = updateConsultation.consultationId.value,
                )
        }

        logger.info("Oppdaterert diagnosetabell med rows: {}", updatedRows)

        updateConsultation.journalNotat?.let { journalnotat ->
            updatedRows +=
                updateJournalnotat(
                    consultationId = updateConsultation.consultationId,
                    patientId = patientId,
                    journalnotat = journalnotat,
                )
        }

        logger.info("Oppdaterert journalnotattable med rows: {}", updatedRows)

        if (updateConsultation.finalize) {
            updatedRows +=
                finalize(consultationId = updateConsultation.consultationId, patientId = patientId)
        }

        logger.info(
            "totalt oppdater diagnose, journalnotat og konsultasjontable med rows: {}",
            updatedRows,
        )

        updatedRows
    }

    private fun finalize(consultationId: ConsultationId, patientId: PatientId): Int =
        ConsultationTable.update({
            (ConsultationTable.id eq consultationId.value) and
                (ConsultationTable.patientId eq patientId.value)
        }) {
            it[endedAt] = LocalDateTime.now()
            it[status] = ConsultationStatus.FINISHED
        }

    suspend fun cancel(
        consultationId: ConsultationId,
        patientId: PatientId,
        orgNumber: String?,
    ): Int = dbQuery {
        logger.info("Avbryter konsultasjon {}", consultationId)
        ConsultationTable.update({
            (ConsultationTable.id eq consultationId.value) and
                (ConsultationTable.patientId eq patientId.value) and
                consultationAtOrg(orgNumber) and
                (ConsultationTable.status eq ConsultationStatus.IN_PROGRESS)
        }) {
            it[endedAt] = LocalDateTime.now()
            it[status] = ConsultationStatus.CANCELLED
        }
    }

    suspend fun updateJournalnotat(
        consultationId: ConsultationId,
        patientId: PatientId,
        journalnotat: String,
    ): Int = dbQuery {
        val eksisterendeId =
            JournalnotatTable.select(JournalnotatTable.id)
                .where { JournalnotatTable.consultationId eq consultationId.value }
                .limit(1)
                .singleOrNull()
                ?.get(JournalnotatTable.id)

        if (eksisterendeId != null) {
            JournalnotatTable.update({ JournalnotatTable.id eq eksisterendeId }) {
                it[JournalnotatTable.journalnotat] = journalnotat
            }
        } else {
            JournalnotatTable.insert {
                    it[JournalnotatTable.consultationId] = consultationId.value
                    it[JournalnotatTable.patientId] = patientId.value
                    it[JournalnotatTable.journalnotat] = journalnotat
                }
                .insertedCount
        }
    }

    suspend fun insertJournalnotat(journalnotat: Journalnotat, orgNumber: String): Int = dbQuery {
        val consultationPatientId =
            ConsultationTable.select(ConsultationTable.patientId)
                .where {
                    (ConsultationTable.id eq journalnotat.consultationId.value) and
                        consultationAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(ConsultationTable.patientId)
                ?: throw ConsultationNotFoundException(journalnotat.consultationId)
        if (consultationPatientId != journalnotat.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                journalnotat.consultationId,
                journalnotat.patientId,
            )
        }

        JournalnotatTable.upsert(
                onUpdateExclude = listOf(JournalnotatTable.id),
                where = { JournalnotatTable.consultationId eq journalnotat.consultationId.value },
            ) {
                it[JournalnotatTable.id] = journalnotat.id.value
                it[JournalnotatTable.consultationId] = journalnotat.consultationId.value
                it[JournalnotatTable.patientId] = journalnotat.patientId.value
                it[JournalnotatTable.journalnotat] = journalnotat.journalnotat
            }
            .insertedCount
    }

    suspend fun updateDiagnosis(diagnosis: CreateDiagnosisRequest, consultationId: Uuid): Int =
        dbQuery {
            val kodeverkDiagnosis =
                Diagnose.from(diagnosis.system, diagnosis.code)
                    ?: throw InvalidDiagnosisException(diagnosis.code, diagnosis.system.toString())

            ConsultationDiagnosisCodeTable.insertIgnore {
                    it[ConsultationDiagnosisCodeTable.consultationId] = consultationId
                    it[diagnosisCode] = kodeverkDiagnosis.code
                    it[diagnosisSystem] = kodeverkDiagnosis.system.toString()
                }
                .insertedCount
        }

    private fun removeDiagnosesNotIn(
        diagnoses: List<CreateDiagnosisRequest>,
        consultationId: Uuid,
    ): Int {
        val onskedeNokler =
            diagnoses
                .map { diagnosis ->
                    Diagnose.from(diagnosis.system, diagnosis.code)
                        ?: throw InvalidDiagnosisException(
                            diagnosis.code,
                            diagnosis.system.toString(),
                        )
                }
                .map { it.system.toString() to it.code }
                .toSet()

        val eksisterendeNokler =
            ConsultationDiagnosisCodeTable.select(
                    ConsultationDiagnosisCodeTable.diagnosisSystem,
                    ConsultationDiagnosisCodeTable.diagnosisCode,
                )
                .where { ConsultationDiagnosisCodeTable.consultationId eq consultationId }
                .map {
                    it[ConsultationDiagnosisCodeTable.diagnosisSystem] to
                        it[ConsultationDiagnosisCodeTable.diagnosisCode]
                }

        return eksisterendeNokler
            .filterNot { it in onskedeNokler }
            .sumOf { (system, code) ->
                ConsultationDiagnosisCodeTable.deleteWhere {
                    (ConsultationDiagnosisCodeTable.consultationId eq consultationId) and
                        (ConsultationDiagnosisCodeTable.diagnosisSystem eq system) and
                        (ConsultationDiagnosisCodeTable.diagnosisCode eq code)
                }
            }
    }

    private fun toEpjConsultation(consultation: ResultRow): Consultation {
        val hprListe =
            ConsultationPractitioner.select(ConsultationPractitioner.hpr)
                .where {
                    ConsultationPractitioner.consultationId eq consultation[ConsultationTable.id]
                }
                .map { it[ConsultationPractitioner.hpr] }

        val journalnotatListe =
            JournalnotatTable.selectAll()
                .where { (JournalnotatTable.consultationId eq consultation[ConsultationTable.id]) }
                .map { it.toJournalnotat() }

        val diagnosisList =
            ConsultationDiagnosisCodeTable.selectAll()
                .where {
                    (ConsultationDiagnosisCodeTable.consultationId eq
                        consultation[ConsultationTable.id])
                }
                .map { it.toDiagnosis() }

        return consultation.toConsultationWithHprAndJournalnotat(
            hprListe,
            journalnotatListe,
            diagnosisList,
        )
    }

    suspend fun findJournalnotat(
        journalnotatId: JournalnotatId,
        orgNumber: String?,
    ): Journalnotat? = dbQuery {
        JournalnotatTable.selectAll()
            .where {
                (JournalnotatTable.id eq journalnotatId.value) and
                    (JournalnotatTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
            }
            .singleOrNull()
            ?.toJournalnotat()
    }

    suspend fun listJournalnotat(
        patientId: PatientId,
        consultationId: ConsultationId?,
        orgNumber: String?,
    ): List<Journalnotat> = dbQuery {
        JournalnotatTable.selectAll()
            .where {
                val atOrg =
                    JournalnotatTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber)
                if (consultationId != null) {
                    (JournalnotatTable.patientId eq patientId.value) and
                        (JournalnotatTable.consultationId eq consultationId.value) and
                        atOrg
                } else {
                    (JournalnotatTable.patientId eq patientId.value) and atOrg
                }
            }
            .map { it.toJournalnotat() }
    }

    suspend fun opprettJournalnotat(
        id: JournalnotatId,
        request: OpprettJournalnotatRequest,
        orgNumber: String,
    ): Journalnotat = dbQuery {
        val consultationPatientId =
            ConsultationTable.select(ConsultationTable.patientId)
                .where {
                    (ConsultationTable.id eq request.consultationId.value) and
                        consultationAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(ConsultationTable.patientId)
                ?: throw ConsultationNotFoundException(request.consultationId)

        if (consultationPatientId != request.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                request.consultationId,
                request.patientId,
            )
        }

        val insertedCount =
            JournalnotatTable.insertIgnore {
                    it[JournalnotatTable.id] = id.value
                    it[JournalnotatTable.consultationId] = request.consultationId.value
                    it[JournalnotatTable.patientId] = request.patientId.value
                    it[JournalnotatTable.journalnotat] = request.journalnotat
                }
                .insertedCount

        if (insertedCount == 0) {
            throw DuplikatJournalnotatException()
        }

        Journalnotat(
            id = id,
            consultationId = request.consultationId,
            patientId = request.patientId,
            journalnotat = request.journalnotat,
        )
    }

    fun ResultRow.toJournalnotat(): Journalnotat =
        Journalnotat(
            id = JournalnotatId(this[JournalnotatTable.id]),
            consultationId = ConsultationId(this[JournalnotatTable.consultationId]),
            patientId = PatientId(this[JournalnotatTable.patientId]),
            journalnotat = this[JournalnotatTable.journalnotat],
        )

    fun ResultRow.toConsultationWithHprAndJournalnotat(
        hprListe: List<String>,
        journalnotatListe: List<Journalnotat>,
        diagnosisList: List<Diagnose>,
    ): Consultation =
        Consultation(
            id = ConsultationId(this[ConsultationTable.id]),
            patientId = PatientId(this[ConsultationTable.patientId]),
            clinicId = ClinicId(this[ConsultationTable.clinicId]),
            hpr = hprListe,
            startedAt = this[ConsultationTable.startedAt],
            endedAt = this[ConsultationTable.endedAt],
            status = this[ConsultationTable.status],
            reasonForVisit = this[ConsultationTable.reasonForVisit],
            journalnotat = journalnotatListe,
            diagnoses = diagnosisList,
        )

    fun ResultRow.toDiagnosis(): Diagnose {
        val system = this[ConsultationDiagnosisCodeTable.diagnosisSystem]
        val code = this[ConsultationDiagnosisCodeTable.diagnosisCode]
        return Diagnose.from(system, code) ?: throw InvalidDiagnosisException(code, system)
    }
}
