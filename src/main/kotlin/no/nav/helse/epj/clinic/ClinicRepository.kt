package no.nav.helse.epj.clinic

import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicTable
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class ClinicRepository {

    suspend fun findByClinicId(id: Uuid): Clinic? {
        return dbQuery {
            val clinic =
                ClinicTable.selectAll().where { ClinicTable.id eq id }.singleOrNull()
                    ?: return@dbQuery null
            clinic.toClinic()
        }
    }

    suspend fun findByOrgNumber(orgNumber: String): Clinic? = dbQuery {
        ClinicTable.selectAll()
            .where { ClinicTable.orgNumber eq orgNumber }
            .singleOrNull()
            ?.toClinic()
    }

    suspend fun listChoosable(): List<Clinic> = dbQuery {
        ClinicTable.selectAll().orderBy(ClinicTable.name).map { it.toClinic() }
    }

    fun ResultRow.toClinic(): Clinic {
        return Clinic(
            id = ClinicId(this[ClinicTable.id]),
            name = this[ClinicTable.name],
            orgNumber = this[ClinicTable.orgNumber],
            phone = this[ClinicTable.phone],
        )
    }
}
