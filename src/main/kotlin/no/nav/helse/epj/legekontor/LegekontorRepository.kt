package no.nav.helse.epj.legekontor

import kotlin.uuid.Uuid
import no.nav.helse.core.db.LegekontorTable
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class LegekontorRepository {

  suspend fun findByLegekontorId(id: Uuid): Legekontor? {
    return dbQuery {
      val legekontor =
        LegekontorTable.selectAll().where { LegekontorTable.id eq id }.singleOrNull()
          ?: return@dbQuery null
      legekontor.toLegekontor()
    }
  }

  fun ResultRow.toLegekontor(): Legekontor {
    return Legekontor(
      id = LegekontorId(this[LegekontorTable.id]),
      navn = this[LegekontorTable.navn],
      orgnummer = this[LegekontorTable.orgnummer],
      tlf = this[LegekontorTable.tlf],
    )
  }
}
