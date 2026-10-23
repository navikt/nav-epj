package no.nav.helse.epj.pasient

import kotlin.uuid.Uuid
import no.nav.helse.core.db.FastlegelistePasientTable
import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.LegekontorTable
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.db.konsultasjonAtOrg
import no.nav.helse.core.utils.DuplikatPasientException
import no.nav.helse.core.utils.IngenFastlegelisteException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class PasientRepository {

    private val logger = logger()

    suspend fun findById(id: Uuid) = dbQuery {
        logger.info("Looking up pasient by id: $id")
        val patient = PasientTable.selectAll().where { PasientTable.id eq id }.singleOrNull()
        patient?.toPasient()
    }

    suspend fun findByPersonident(personident: String) = dbQuery {
        logger.info("Looking up pasient by personident")
        val patient =
            PasientTable.selectAll()
                .where { PasientTable.personident eq personident }
                .singleOrNull()
        patient?.toPasient()
    }

    suspend fun listAtLegekontor(
        orgnummer: String,
        ownHpr: HelsepersonellHpr,
    ): List<PasientListeElement> = dbQuery {
        val rows =
            FastlegelistePasientTable.join(
                    FastlegelisteTable,
                    JoinType.INNER,
                    FastlegelistePasientTable.fastlegelisteId,
                    FastlegelisteTable.id,
                )
                .join(
                    LegekontorTable,
                    JoinType.INNER,
                    FastlegelisteTable.legekontorId,
                    LegekontorTable.id,
                )
                .join(
                    PasientTable,
                    JoinType.INNER,
                    FastlegelistePasientTable.pasientId,
                    PasientTable.id,
                )
                .selectAll()
                .where { LegekontorTable.orgnummer eq orgnummer }
                .toList()

        val listedIds = rows.map { it[PasientTable.id] }
        val lapsedRows =
            PasientTable.selectAll()
                .where {
                    (PasientTable.id inSubQuery konsultasjonPasientIdsAtOrg(orgnummer)) and
                        (PasientTable.id notInList listedIds)
                }
                .toList()

        val listed =
            rows
                .sortedWith(
                    compareBy<ResultRow> { it[FastlegelisteTable.hpr] != ownHpr.value }
                        .thenBy { it[PasientTable.etternavn] }
                        .thenBy { it[PasientTable.fornavn] }
                )
                .map { PasientListeElement(it.toPasient(), selectable = true) }
        val lapsed =
            lapsedRows
                .sortedWith(
                    compareBy<ResultRow> { it[PasientTable.etternavn] }
                        .thenBy { it[PasientTable.fornavn] }
                )
                .map { PasientListeElement(it.toPasient(), selectable = false) }
        listed + lapsed
    }

    suspend fun hasKonsultasjonAt(pasientId: Uuid, orgnummer: String): Boolean = dbQuery {
        KonsultasjonTable.select(KonsultasjonTable.id)
            .where { (KonsultasjonTable.pasientId eq pasientId) and konsultasjonAtOrg(orgnummer) }
            .any()
    }

    private fun konsultasjonPasientIdsAtOrg(orgnummer: String) =
        KonsultasjonTable.select(KonsultasjonTable.pasientId).where { konsultasjonAtOrg(orgnummer) }

    suspend fun isListedAt(pasientId: Uuid, orgnummer: String): Boolean = dbQuery {
        FastlegelistePasientTable.join(
                FastlegelisteTable,
                JoinType.INNER,
                FastlegelistePasientTable.fastlegelisteId,
                FastlegelisteTable.id,
            )
            .join(
                LegekontorTable,
                JoinType.INNER,
                FastlegelisteTable.legekontorId,
                LegekontorTable.id,
            )
            .select(FastlegelistePasientTable.pasientId)
            .where {
                (FastlegelistePasientTable.pasientId eq pasientId) and
                    (LegekontorTable.orgnummer eq orgnummer)
            }
            .any()
    }

    suspend fun insertOnList(
        pasient: Pasient,
        listHpr: HelsepersonellHpr,
        orgnummer: String,
    ): Unit = dbQuery {
        val listId =
            FastlegelisteTable.join(
                    LegekontorTable,
                    JoinType.INNER,
                    FastlegelisteTable.legekontorId,
                    LegekontorTable.id,
                )
                .select(FastlegelisteTable.id)
                .where {
                    (FastlegelisteTable.hpr eq listHpr.value) and
                        (LegekontorTable.orgnummer eq orgnummer)
                }
                .singleOrNull()
                ?.get(FastlegelisteTable.id) ?: throw IngenFastlegelisteException()

        insertPasient(pasient)
        FastlegelistePasientTable.insert {
            it[fastlegelisteId] = listId
            it[pasientId] = pasient.id.value
        }
        Unit
    }

    suspend fun insert(pasient: Pasient) = dbQuery { insertPasient(pasient) }

    private fun insertPasient(pasient: Pasient) {
        logger.info("Inserting pasient with id: ${pasient.id.value}")

        val insertStatement = PasientTable.insertIgnore {
            it[id] = pasient.id.value
            it[fornavn] = pasient.fornavn
            it[etternavn] = pasient.etternavn
            it[personident] = pasient.personident
            it[personidentType] = pasient.personidentType
            it[birthDate] = pasient.birthDate
            it[gender] = pasient.gender
        }

        if (insertStatement.insertedCount == 0) {
            throw DuplikatPasientException()
        }
    }

    private fun ResultRow.toPasient(): Pasient =
        Pasient(
            id = PasientId(this[PasientTable.id]),
            fornavn = this[PasientTable.fornavn],
            etternavn = this[PasientTable.etternavn],
            personident = this[PasientTable.personident],
            personidentType = this[PasientTable.personidentType],
            birthDate = this[PasientTable.birthDate],
            gender = this[PasientTable.gender],
        )
}
