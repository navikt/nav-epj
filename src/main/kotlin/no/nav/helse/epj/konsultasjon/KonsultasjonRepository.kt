package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.db.JournalnotatTable
import no.nav.helse.core.db.KonsultasjonDiagnosekodeTable
import no.nav.helse.core.db.KonsultasjonHelsepersonell
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.pasient.PasientId
import no.nav.tsm.diagnoser.Diagnose
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

class KonsultasjonRepository {
    private val logger = logger()

    suspend fun listByPasientId(id: PasientId): List<Konsultasjon> = dbQuery {
        val konsultasjoner =
            KonsultasjonTable.selectAll()
                .where { (KonsultasjonTable.pasientId eq id.value) }
                .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
                .toList()

        val konsultasjonIder = konsultasjoner.map { it[KonsultasjonTable.id] }
        val hprByKonsultasjonId =
            KonsultasjonHelsepersonell.selectAll()
                .where { KonsultasjonHelsepersonell.konsultasjonId inList konsultasjonIder }
                .groupBy(
                    keySelector = { it[KonsultasjonHelsepersonell.konsultasjonId] },
                    valueTransform = { it[KonsultasjonHelsepersonell.hpr] },
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

    suspend fun listDiagnoser(id: PasientId): List<Diagnose> = dbQuery {
        val konsultasjonIder =
            KonsultasjonTable.select(KonsultasjonTable.id)
                .where { KonsultasjonTable.pasientId eq id.value }
                .map { it[KonsultasjonTable.id] }

        if (konsultasjonIder.isEmpty()) {
            return@dbQuery emptyList()
        }

        KonsultasjonDiagnosekodeTable.selectAll()
            .where { KonsultasjonDiagnosekodeTable.konsultasjonId inList konsultasjonIder }
            .map { it.toDiagnose() }
    }

    suspend fun listDiagnoser(id: KonsultasjonId): List<Diagnose> = dbQuery {
        KonsultasjonDiagnosekodeTable.selectAll()
            .where { KonsultasjonDiagnosekodeTable.konsultasjonId eq id.value }
            .map { it.toDiagnose() }
    }

    suspend fun insert(opprettKonsultasjon: OpprettKonsultasjon) = dbQuery {
        val konsultasjon =
            KonsultasjonTable.insertReturning {
                    it[pasientId] = opprettKonsultasjon.pasientId.value
                    it[startetTidspunkt] = opprettKonsultasjon.startetTidspunkt
                    it[status] = opprettKonsultasjon.status
                }
                .single()
        val id = konsultasjon[KonsultasjonTable.id]
        opprettKonsultasjon.hpr.forEach { hprValue ->
            KonsultasjonHelsepersonell.insert {
                it[konsultasjonId] = id
                it[hpr] = hprValue.value
            }
        }
        KonsultasjonId(konsultasjon[KonsultasjonTable.id])
    }

    suspend fun findActiveByPasientId(pasientId: PasientId): Konsultasjon? {
        val pasientUuid = pasientId.value
        return dbQuery {
            val konsultasjon =
                KonsultasjonTable.selectAll()
                    .where {
                        (KonsultasjonTable.pasientId eq pasientUuid) and
                            KonsultasjonTable.avsluttetTidspunkt.isNull()
                    }
                    .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull() ?: return@dbQuery null
            toEpjKonsultasjon(konsultasjon)
        }
    }

    suspend fun findByKonsultasjonId(konsultasjonId: KonsultasjonId): Konsultasjon? {
        return dbQuery {
            val konsultasjon =
                KonsultasjonTable.selectAll()
                    .where { KonsultasjonTable.id eq konsultasjonId.value }
                    .singleOrNull() ?: return@dbQuery null
            toEpjKonsultasjon(konsultasjon)
        }
    }

    suspend fun update(
        oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
        pasientId: PasientId,
    ): Int = dbQuery {
        logger.info("Oppdaterer konsultasjon {}", oppdaterKonsultasjon.konsultasjonId)

        val konsultasjonPasient =
            KonsultasjonTable.selectAll()
                .where {
                    (KonsultasjonTable.id eq oppdaterKonsultasjon.konsultasjonId.value) and
                        (KonsultasjonTable.pasientId eq pasientId.value)
                }
                .limit(1)
                .any()

        if (!konsultasjonPasient) {
            logger.warn(
                "Fant ikke konsultasjon {} for pasientId {}, avbryter oppdatering",
                oppdaterKonsultasjon.konsultasjonId,
                pasientId,
            )
            return@dbQuery 0
        }

        var updatedRows = 0

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
                    pasientId = pasientId,
                    journalnotat = journalnotat,
                )
        }

        logger.info("Oppdaterert journalnotattable med rows: {}", updatedRows)

        if (oppdaterKonsultasjon.ferdigstill) {
            updatedRows +=
                ferdigstill(
                    konsultasjonId = oppdaterKonsultasjon.konsultasjonId,
                    pasientId = pasientId,
                )
        }

        logger.info(
            "totalt oppdater diagnose, journalnotat og konsultasjontable med rows: {}",
            updatedRows,
        )

        updatedRows
    }

    private fun ferdigstill(konsultasjonId: KonsultasjonId, pasientId: PasientId): Int =
        KonsultasjonTable.update({
            (KonsultasjonTable.id eq konsultasjonId.value) and
                (KonsultasjonTable.pasientId eq pasientId.value)
        }) {
            it[avsluttetTidspunkt] = LocalDateTime.now()
            it[status] = KonsultasjonStatus.FULLFØRT
        }

    suspend fun updateJournalnotat(
        konsultasjonId: KonsultasjonId,
        pasientId: PasientId,
        journalnotat: String,
    ): Int = dbQuery {
        JournalnotatTable.insert {
                it[JournalnotatTable.konsultasjonId] = konsultasjonId.value
                it[JournalnotatTable.pasientId] = pasientId.value
                it[JournalnotatTable.journalnotat] = journalnotat
            }
            .insertedCount
    }

    suspend fun insertJournalnotat(journalnotat: Journalnotat): Int = dbQuery {
        JournalnotatTable.upsert(onUpdateExclude = listOf(JournalnotatTable.id)) {
                it[JournalnotatTable.id] = journalnotat.id.value
                it[JournalnotatTable.konsultasjonId] = journalnotat.konsultasjonId.value
                it[JournalnotatTable.pasientId] = journalnotat.pasientId.value
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

    private fun toEpjKonsultasjon(konsultasjon: ResultRow): Konsultasjon {
        val hprListe =
            KonsultasjonHelsepersonell.select(KonsultasjonHelsepersonell.hpr)
                .where {
                    KonsultasjonHelsepersonell.konsultasjonId eq konsultasjon[KonsultasjonTable.id]
                }
                .map { it[KonsultasjonHelsepersonell.hpr] }

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

    suspend fun findJournalnotat(journalnotatId: JournalnotatId): Journalnotat? = dbQuery {
        JournalnotatTable.selectAll()
            .where { (JournalnotatTable.id eq journalnotatId.value) }
            .singleOrNull()
            ?.toJournalnotat()
    }

    fun ResultRow.toJournalnotat(): Journalnotat =
        Journalnotat(
            id = JournalnotatId(this[JournalnotatTable.id]),
            konsultasjonId = KonsultasjonId(this[JournalnotatTable.konsultasjonId]),
            pasientId = PasientId(this[JournalnotatTable.pasientId]),
            journalnotat = this[JournalnotatTable.journalnotat],
        )

    fun ResultRow.toKonsultasjonWithHprAndJournalnotat(
        hprListe: List<String>,
        journalnotatListe: List<Journalnotat>,
        diagnoseListe: List<Diagnose>,
    ): Konsultasjon =
        Konsultasjon(
            id = KonsultasjonId(this[KonsultasjonTable.id]),
            pasientId = PasientId(this[KonsultasjonTable.pasientId]),
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
