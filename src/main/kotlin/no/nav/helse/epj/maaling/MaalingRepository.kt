package no.nav.helse.epj.maaling

import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.MaalingTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.DuplikatMaalingException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PasientId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class MaalingRepository {
    private val logger = logger()

    suspend fun insert(maaling: Maaling): Unit = dbQuery {
        val konsultasjonPasientId =
            KonsultasjonTable.select(KonsultasjonTable.pasientId)
                .where { KonsultasjonTable.id eq maaling.konsultasjonId.value }
                .singleOrNull()
                ?.get(KonsultasjonTable.pasientId)
                ?: throw KonsultasjonNotFoundException(maaling.konsultasjonId)

        if (konsultasjonPasientId != maaling.pasientId.value) {
            throw KonsultasjonTilhorerAnnenPasientException(
                maaling.konsultasjonId,
                maaling.pasientId,
            )
        }

        logger.info("Inserting maaling with id: ${maaling.id.value}")

        val insertedCount =
            MaalingTable.insertIgnore {
                    it[id] = maaling.id.value
                    it[pasientId] = maaling.pasientId.value
                    it[konsultasjonId] = maaling.konsultasjonId.value
                    it[hpr] = maaling.hpr?.value
                    it[loincKode] = maaling.loincKode
                    it[loincVisningsnavn] = maaling.loincVisningsnavn
                    it[verdi] = maaling.verdi
                    it[enhetKode] = maaling.enhetKode
                    it[enhetVisningsnavn] = maaling.enhetVisningsnavn
                    it[effektivTidspunkt] = maaling.effektivTidspunkt
                    it[status] = maaling.status
                }
                .insertedCount

        if (insertedCount == 0) {
            throw DuplikatMaalingException()
        }
    }

    suspend fun findById(id: MaalingId): Maaling? = dbQuery {
        MaalingTable.selectAll().where { MaalingTable.id eq id.value }.singleOrNull()?.toMaaling()
    }

    suspend fun listByPasientId(pasientId: PasientId): List<Maaling> = dbQuery {
        MaalingTable.selectAll()
            .where { MaalingTable.pasientId eq pasientId.value }
            .orderBy(MaalingTable.effektivTidspunkt, SortOrder.ASC)
            .map { it.toMaaling() }
    }

    suspend fun listByKonsultasjonId(konsultasjonId: KonsultasjonId): List<Maaling> = dbQuery {
        MaalingTable.selectAll()
            .where { MaalingTable.konsultasjonId eq konsultasjonId.value }
            .orderBy(MaalingTable.effektivTidspunkt, SortOrder.ASC)
            .map { it.toMaaling() }
    }

    private fun ResultRow.toMaaling(): Maaling =
        Maaling(
            id = MaalingId(this[MaalingTable.id]),
            pasientId = PasientId(this[MaalingTable.pasientId]),
            konsultasjonId = KonsultasjonId(this[MaalingTable.konsultasjonId]),
            hpr = this[MaalingTable.hpr]?.let { HelsepersonellHpr(it) },
            loincKode = this[MaalingTable.loincKode],
            loincVisningsnavn = this[MaalingTable.loincVisningsnavn],
            verdi = this[MaalingTable.verdi],
            enhetKode = this[MaalingTable.enhetKode],
            enhetVisningsnavn = this[MaalingTable.enhetVisningsnavn],
            effektivTidspunkt = this[MaalingTable.effektivTidspunkt],
            status = this[MaalingTable.status],
        )
}
