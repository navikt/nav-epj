package no.nav.helse.epj.sessionorg

import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.clinic.ClinicId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

class FastlegelisteRepository {
    suspend fun findClinicIdsByHpr(hpr: String): List<ClinicId> = dbQuery {
        FastlegelisteTable.select(FastlegelisteTable.clinicId)
            .where { FastlegelisteTable.hpr eq hpr }
            .map { ClinicId(it[FastlegelisteTable.clinicId]) }
            .distinct()
    }
}
