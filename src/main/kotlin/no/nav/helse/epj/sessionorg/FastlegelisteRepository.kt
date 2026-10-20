package no.nav.helse.epj.sessionorg

import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.legekontor.LegekontorId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

class FastlegelisteRepository {
    suspend fun findLegekontorIdsByHpr(hpr: String): List<LegekontorId> = dbQuery {
        FastlegelisteTable.select(FastlegelisteTable.legekontorId)
            .where { FastlegelisteTable.hpr eq hpr }
            .map { LegekontorId(it[FastlegelisteTable.legekontorId]) }
            .distinct()
    }
}
