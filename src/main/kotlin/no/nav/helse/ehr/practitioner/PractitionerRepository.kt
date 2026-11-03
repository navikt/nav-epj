package no.nav.helse.ehr.practitioner

import no.nav.helse.core.db.PractitionerTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

class PractitionerRepository {

    private val logger = logger()

    suspend fun insert(practitioner: CreatePractitioner) = dbQuery {
        logger.info("Inserting helsepersonell: ${practitioner.name}")
        PractitionerTable.insertIgnore {
            it[hpr] = practitioner.hpr.value
            it[name] = practitioner.name
            it[qualification] = practitioner.qualification
        }
    }

    suspend fun findByHpr(hpr: PractitionerHpr) = dbQuery {
        logger.info("looking up helsepersonell: $hpr")
        PractitionerTable.selectAll()
            .where { PractitionerTable.hpr eq hpr.value }
            .singleOrNull()
            ?.toPractitioner()
    }

    private fun ResultRow.toPractitioner() =
        Practitioner(
            hpr = PractitionerHpr(this[PractitionerTable.hpr]),
            name = this[PractitionerTable.name],
            qualification = this[PractitionerTable.qualification],
        )
}
