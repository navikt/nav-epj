package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicTable
import no.nav.helse.core.db.JournalnotatTable
import no.nav.helse.core.db.KonsultasjonDiagnosekodeTable
import no.nav.helse.core.db.KonsultasjonPractitioner
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.db.konsultasjonAtOrg
import no.nav.helse.core.db.konsultasjonIdsAtOrg
import no.nav.helse.core.utils.ClinicNotFoundException
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.DuplikatJournalnotatException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
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

class KonsultasjonRepository {
    private val logger = logger()

    suspend fun listByPatientId(id: PatientId, orgNumber: String?): List<Konsultasjon> = dbQuery {
        val konsultasjoner =
            KonsultasjonTable.selectAll()
                .where {
                    (KonsultasjonTable.patientId eq id.value) and konsultasjonAtOrg(orgNumber)
                }
                .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
                .toList()

        val konsultasjonIder = konsultasjoner.map { it[KonsultasjonTable.id] }
        val hprByKonsultasjonId =
            KonsultasjonPractitioner.selectAll()
                .where { KonsultasjonPractitioner.konsultasjonId inList konsultasjonIder }
                .groupBy(
                    keySelector = { it[KonsultasjonPractitioner.konsultasjonId] },
                    valueTransform = { it[KonsultasjonPractitioner.hpr] },
                )

        val journalnotatByKonsultasjonId =
            JournalnotatTable.selectAll()
                .where { JournalnotatTable.konsultasjonId inList konsultasjonIder }
                .groupBy(
                    keySelector = { it[JournalnotatTable.konsultasjonId] },
                    valueTransform = { it.toJournalnotat() },
                )

        val diagnoserByKonsultasjonId =
            KonsultasjonDiagnosekodeTable.selectAll()
                .where { KonsultasjonDiagnosekodeTable.konsultasjonId inList konsultasjonIder }
                .groupBy(
                    keySelector = { it[KonsultasjonDiagnosekodeTable.konsultasjonId] },
                    valueTransform = { it.toDiagnose() },
                )

        konsultasjoner.map { row ->
            val konsultasjonId = row[KonsultasjonTable.id]
            val hprListe = hprByKonsultasjonId[konsultasjonId].orEmpty()
            val journalnotatListe = journalnotatByKonsultasjonId[konsultasjonId].orEmpty()
            val diagnoseListe = diagnoserByKonsultasjonId[konsultasjonId].orEmpty()
            row.toKonsultasjonWithHprAndJournalnotat(hprListe, journalnotatListe, diagnoseListe)
        }
    }

    suspend fun listDiagnoser(id: PatientId, orgNumber: String?): List<Diagnose> = dbQuery {
        val konsultasjonIder =
            KonsultasjonTable.select(KonsultasjonTable.id)
                .where {
                    (KonsultasjonTable.patientId eq id.value) and konsultasjonAtOrg(orgNumber)
                }
                .map { it[KonsultasjonTable.id] }

        if (konsultasjonIder.isEmpty()) {
            return@dbQuery emptyList()
        }

        KonsultasjonDiagnosekodeTable.selectAll()
            .where { KonsultasjonDiagnosekodeTable.konsultasjonId inList konsultasjonIder }
            .map { it.toDiagnose() }
    }

    suspend fun listDiagnoser(id: KonsultasjonId, orgNumber: String?): List<Diagnose> = dbQuery {
        KonsultasjonDiagnosekodeTable.selectAll()
            .where {
                (KonsultasjonDiagnosekodeTable.konsultasjonId eq id.value) and
                    (KonsultasjonDiagnosekodeTable.konsultasjonId inSubQuery
                        konsultasjonIdsAtOrg(orgNumber))
            }
            .map { it.toDiagnose() }
    }

    suspend fun insert(opprettKonsultasjon: OpprettKonsultasjon) = dbQuery {
        val clinicId =
            ClinicTable.select(ClinicTable.id)
                .where { ClinicTable.orgNumber eq opprettKonsultasjon.orgNumber }
                .singleOrNull()
                ?.get(ClinicTable.id)
                ?: throw ClinicNotFoundException(opprettKonsultasjon.orgNumber)
        val konsultasjon =
            KonsultasjonTable.insertReturning {
                    it[patientId] = opprettKonsultasjon.patientId.value
                    it[KonsultasjonTable.clinicId] = clinicId
                    it[startetTidspunkt] = opprettKonsultasjon.startetTidspunkt
                    it[status] = opprettKonsultasjon.status
                }
                .single()
        val id = konsultasjon[KonsultasjonTable.id]
        opprettKonsultasjon.hpr.forEach { hprValue ->
            KonsultasjonPractitioner.insert {
                it[konsultasjonId] = id
                it[hpr] = hprValue.value
            }
        }
        KonsultasjonId(konsultasjon[KonsultasjonTable.id])
    }

    suspend fun findActiveByPatientId(patientId: PatientId, orgNumber: String?): Konsultasjon? {
        val patientUuid = patientId.value
        return dbQuery {
            val konsultasjon =
                KonsultasjonTable.selectAll()
                    .where {
                        (KonsultasjonTable.patientId eq patientUuid) and
                            (KonsultasjonTable.status eq KonsultasjonStatus.PAAGAAENDE) and
                            konsultasjonAtOrg(orgNumber)
                    }
                    .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull() ?: return@dbQuery null
            toEpjKonsultasjon(konsultasjon)
        }
    }

    suspend fun findActiveByPatientIdAndHpr(
        patientId: PatientId,
        hpr: PractitionerHpr,
        orgNumber: String?,
    ): Konsultasjon? {
        val patientUuid = patientId.value
        return dbQuery {
            val konsultasjon =
                (KonsultasjonTable innerJoin KonsultasjonPractitioner)
                    .selectAll()
                    .where {
                        (KonsultasjonTable.patientId eq patientUuid) and
                            (KonsultasjonTable.status eq KonsultasjonStatus.PAAGAAENDE) and
                            (KonsultasjonPractitioner.hpr eq hpr.value) and
                            konsultasjonAtOrg(orgNumber)
                    }
                    .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull() ?: return@dbQuery null
            toEpjKonsultasjon(konsultasjon)
        }
    }

    suspend fun findByKonsultasjonId(
        konsultasjonId: KonsultasjonId,
        orgNumber: String?,
    ): Konsultasjon? {
        return dbQuery {
            val konsultasjon =
                KonsultasjonTable.selectAll()
                    .where {
                        (KonsultasjonTable.id eq konsultasjonId.value) and
                            konsultasjonAtOrg(orgNumber)
                    }
                    .singleOrNull() ?: return@dbQuery null
            toEpjKonsultasjon(konsultasjon)
        }
    }

    suspend fun update(
        oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
        patientId: PatientId,
        orgNumber: String?,
    ): Int = dbQuery {
        logger.info("Oppdaterer konsultasjon {}", oppdaterKonsultasjon.konsultasjonId)

        val konsultasjonPatient =
            KonsultasjonTable.selectAll()
                .where {
                    (KonsultasjonTable.id eq oppdaterKonsultasjon.konsultasjonId.value) and
                        (KonsultasjonTable.patientId eq patientId.value) and
                        konsultasjonAtOrg(orgNumber)
                }
                .limit(1)
                .any()

        if (!konsultasjonPatient) {
            logger.warn(
                "Fant ikke konsultasjon {} for pasientId {}, avbryter oppdatering",
                oppdaterKonsultasjon.konsultasjonId,
                patientId,
            )
            return@dbQuery 0
        }

        var updatedRows = 0

        updatedRows +=
            removeDiagnoserNotIn(
                diagnoser = oppdaterKonsultasjon.diagnoser,
                konsultasjonId = oppdaterKonsultasjon.konsultasjonId.value,
            )

        oppdaterKonsultasjon.diagnoser.forEach { diagnose ->
            updatedRows +=
                updateDiagnose(
                    diagnose = diagnose,
                    konsultasjonId = oppdaterKonsultasjon.konsultasjonId.value,
                )
        }

        logger.info("Oppdaterert diagnosetabell med rows: {}", updatedRows)

        oppdaterKonsultasjon.journalNotat?.let { journalnotat ->
            updatedRows +=
                updateJournalnotat(
                    konsultasjonId = oppdaterKonsultasjon.konsultasjonId,
                    patientId = patientId,
                    journalnotat = journalnotat,
                )
        }

        logger.info("Oppdaterert journalnotattable med rows: {}", updatedRows)

        if (oppdaterKonsultasjon.ferdigstill) {
            updatedRows +=
                ferdigstill(
                    konsultasjonId = oppdaterKonsultasjon.konsultasjonId,
                    patientId = patientId,
                )
        }

        logger.info(
            "totalt oppdater diagnose, journalnotat og konsultasjontable med rows: {}",
            updatedRows,
        )

        updatedRows
    }

    private fun ferdigstill(konsultasjonId: KonsultasjonId, patientId: PatientId): Int =
        KonsultasjonTable.update({
            (KonsultasjonTable.id eq konsultasjonId.value) and
                (KonsultasjonTable.patientId eq patientId.value)
        }) {
            it[avsluttetTidspunkt] = LocalDateTime.now()
            it[status] = KonsultasjonStatus.FULLFOERT
        }

    suspend fun avbryt(
        konsultasjonId: KonsultasjonId,
        patientId: PatientId,
        orgNumber: String?,
    ): Int = dbQuery {
        logger.info("Avbryter konsultasjon {}", konsultasjonId)
        KonsultasjonTable.update({
            (KonsultasjonTable.id eq konsultasjonId.value) and
                (KonsultasjonTable.patientId eq patientId.value) and
                konsultasjonAtOrg(orgNumber) and
                (KonsultasjonTable.status eq KonsultasjonStatus.PAAGAAENDE)
        }) {
            it[avsluttetTidspunkt] = LocalDateTime.now()
            it[status] = KonsultasjonStatus.AVLYST
        }
    }

    suspend fun updateJournalnotat(
        konsultasjonId: KonsultasjonId,
        patientId: PatientId,
        journalnotat: String,
    ): Int = dbQuery {
        val eksisterendeId =
            JournalnotatTable.select(JournalnotatTable.id)
                .where { JournalnotatTable.konsultasjonId eq konsultasjonId.value }
                .limit(1)
                .singleOrNull()
                ?.get(JournalnotatTable.id)

        if (eksisterendeId != null) {
            JournalnotatTable.update({ JournalnotatTable.id eq eksisterendeId }) {
                it[JournalnotatTable.journalnotat] = journalnotat
            }
        } else {
            JournalnotatTable.insert {
                    it[JournalnotatTable.konsultasjonId] = konsultasjonId.value
                    it[JournalnotatTable.patientId] = patientId.value
                    it[JournalnotatTable.journalnotat] = journalnotat
                }
                .insertedCount
        }
    }

    suspend fun insertJournalnotat(journalnotat: Journalnotat, orgNumber: String): Int = dbQuery {
        val konsultasjonPatientId =
            KonsultasjonTable.select(KonsultasjonTable.patientId)
                .where {
                    (KonsultasjonTable.id eq journalnotat.konsultasjonId.value) and
                        konsultasjonAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(KonsultasjonTable.patientId)
                ?: throw KonsultasjonNotFoundException(journalnotat.konsultasjonId)
        if (konsultasjonPatientId != journalnotat.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                journalnotat.konsultasjonId,
                journalnotat.patientId,
            )
        }

        JournalnotatTable.upsert(
                onUpdateExclude = listOf(JournalnotatTable.id),
                where = { JournalnotatTable.konsultasjonId eq journalnotat.konsultasjonId.value },
            ) {
                it[JournalnotatTable.id] = journalnotat.id.value
                it[JournalnotatTable.konsultasjonId] = journalnotat.konsultasjonId.value
                it[JournalnotatTable.patientId] = journalnotat.patientId.value
                it[JournalnotatTable.journalnotat] = journalnotat.journalnotat
            }
            .insertedCount
    }

    suspend fun updateDiagnose(diagnose: OpprettDiagnoseRequest, konsultasjonId: Uuid): Int =
        dbQuery {
            val kodeverkDiagnose =
                Diagnose.from(diagnose.system, diagnose.kode)
                    ?: throw UgyldigDiagnoseException(diagnose.kode, diagnose.system.toString())

            KonsultasjonDiagnosekodeTable.insertIgnore {
                    it[KonsultasjonDiagnosekodeTable.konsultasjonId] = konsultasjonId
                    it[diagnosekode] = kodeverkDiagnose.code
                    it[diagnosesystem] = kodeverkDiagnose.system.toString()
                }
                .insertedCount
        }

    private fun removeDiagnoserNotIn(
        diagnoser: List<OpprettDiagnoseRequest>,
        konsultasjonId: Uuid,
    ): Int {
        val onskedeNokler =
            diagnoser
                .map { diagnose ->
                    Diagnose.from(diagnose.system, diagnose.kode)
                        ?: throw UgyldigDiagnoseException(diagnose.kode, diagnose.system.toString())
                }
                .map { it.system.toString() to it.code }
                .toSet()

        val eksisterendeNokler =
            KonsultasjonDiagnosekodeTable.select(
                    KonsultasjonDiagnosekodeTable.diagnosesystem,
                    KonsultasjonDiagnosekodeTable.diagnosekode,
                )
                .where { KonsultasjonDiagnosekodeTable.konsultasjonId eq konsultasjonId }
                .map {
                    it[KonsultasjonDiagnosekodeTable.diagnosesystem] to
                        it[KonsultasjonDiagnosekodeTable.diagnosekode]
                }

        return eksisterendeNokler
            .filterNot { it in onskedeNokler }
            .sumOf { (system, kode) ->
                KonsultasjonDiagnosekodeTable.deleteWhere {
                    (KonsultasjonDiagnosekodeTable.konsultasjonId eq konsultasjonId) and
                        (KonsultasjonDiagnosekodeTable.diagnosesystem eq system) and
                        (KonsultasjonDiagnosekodeTable.diagnosekode eq kode)
                }
            }
    }

    private fun toEpjKonsultasjon(konsultasjon: ResultRow): Konsultasjon {
        val hprListe =
            KonsultasjonPractitioner.select(KonsultasjonPractitioner.hpr)
                .where {
                    KonsultasjonPractitioner.konsultasjonId eq konsultasjon[KonsultasjonTable.id]
                }
                .map { it[KonsultasjonPractitioner.hpr] }

        val journalnotatListe =
            JournalnotatTable.selectAll()
                .where { (JournalnotatTable.konsultasjonId eq konsultasjon[KonsultasjonTable.id]) }
                .map { it.toJournalnotat() }

        val diagnoseListe =
            KonsultasjonDiagnosekodeTable.selectAll()
                .where {
                    (KonsultasjonDiagnosekodeTable.konsultasjonId eq
                        konsultasjon[KonsultasjonTable.id])
                }
                .map { it.toDiagnose() }

        return konsultasjon.toKonsultasjonWithHprAndJournalnotat(
            hprListe,
            journalnotatListe,
            diagnoseListe,
        )
    }

    suspend fun findJournalnotat(
        journalnotatId: JournalnotatId,
        orgNumber: String?,
    ): Journalnotat? = dbQuery {
        JournalnotatTable.selectAll()
            .where {
                (JournalnotatTable.id eq journalnotatId.value) and
                    (JournalnotatTable.konsultasjonId inSubQuery konsultasjonIdsAtOrg(orgNumber))
            }
            .singleOrNull()
            ?.toJournalnotat()
    }

    suspend fun listJournalnotat(
        patientId: PatientId,
        konsultasjonId: KonsultasjonId?,
        orgNumber: String?,
    ): List<Journalnotat> = dbQuery {
        JournalnotatTable.selectAll()
            .where {
                val atOrg =
                    JournalnotatTable.konsultasjonId inSubQuery konsultasjonIdsAtOrg(orgNumber)
                if (konsultasjonId != null) {
                    (JournalnotatTable.patientId eq patientId.value) and
                        (JournalnotatTable.konsultasjonId eq konsultasjonId.value) and
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
        val konsultasjonPatientId =
            KonsultasjonTable.select(KonsultasjonTable.patientId)
                .where {
                    (KonsultasjonTable.id eq request.konsultasjonId.value) and
                        konsultasjonAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(KonsultasjonTable.patientId)
                ?: throw KonsultasjonNotFoundException(request.konsultasjonId)

        if (konsultasjonPatientId != request.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                request.konsultasjonId,
                request.patientId,
            )
        }

        val insertedCount =
            JournalnotatTable.insertIgnore {
                    it[JournalnotatTable.id] = id.value
                    it[JournalnotatTable.konsultasjonId] = request.konsultasjonId.value
                    it[JournalnotatTable.patientId] = request.patientId.value
                    it[JournalnotatTable.journalnotat] = request.journalnotat
                }
                .insertedCount

        if (insertedCount == 0) {
            throw DuplikatJournalnotatException()
        }

        Journalnotat(
            id = id,
            konsultasjonId = request.konsultasjonId,
            patientId = request.patientId,
            journalnotat = request.journalnotat,
        )
    }

    fun ResultRow.toJournalnotat(): Journalnotat =
        Journalnotat(
            id = JournalnotatId(this[JournalnotatTable.id]),
            konsultasjonId = KonsultasjonId(this[JournalnotatTable.konsultasjonId]),
            patientId = PatientId(this[JournalnotatTable.patientId]),
            journalnotat = this[JournalnotatTable.journalnotat],
        )

    fun ResultRow.toKonsultasjonWithHprAndJournalnotat(
        hprListe: List<String>,
        journalnotatListe: List<Journalnotat>,
        diagnoseListe: List<Diagnose>,
    ): Konsultasjon =
        Konsultasjon(
            id = KonsultasjonId(this[KonsultasjonTable.id]),
            patientId = PatientId(this[KonsultasjonTable.patientId]),
            clinicId = ClinicId(this[KonsultasjonTable.clinicId]),
            hpr = hprListe,
            startetTidspunkt = this[KonsultasjonTable.startetTidspunkt],
            avsluttetTidspunkt = this[KonsultasjonTable.avsluttetTidspunkt],
            status = this[KonsultasjonTable.status],
            problemstilling = this[KonsultasjonTable.problemstilling],
            journalnotat = journalnotatListe,
            diagnoser = diagnoseListe,
        )

    fun ResultRow.toDiagnose(): Diagnose {
        val system = this[KonsultasjonDiagnosekodeTable.diagnosesystem]
        val kode = this[KonsultasjonDiagnosekodeTable.diagnosekode]
        return Diagnose.from(system, kode) ?: throw UgyldigDiagnoseException(kode, system)
    }
}
