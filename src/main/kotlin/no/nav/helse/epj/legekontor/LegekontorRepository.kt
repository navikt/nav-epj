package no.nav.helse.epj.legekontor

import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.core.db.LegekontorTable
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll

@OptIn(ExperimentalUuidApi::class)
class LegekontorRepository {

  suspend fun find(): Legekontor? {
    return dbQuery {
      val legekontor = LegekontorTable.selectAll().singleOrNull() ?: return@dbQuery null
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
