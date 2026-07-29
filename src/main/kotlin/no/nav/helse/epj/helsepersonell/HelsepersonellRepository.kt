package no.nav.helse.epj.helsepersonell

import kotlin.uuid.ExperimentalUuidApi
import no.nav.helse.core.db.HelsepersonellTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.legekontor.LegekontorId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

@OptIn(ExperimentalUuidApi::class)
class HelsepersonellRepository {

  private val logger = logger()

  suspend fun insert(helsePersonell: OpprettHelsepersonell) = dbQuery {
    logger.info("Inserting helsepersonell: ${helsePersonell.navn}")
    HelsepersonellTable.insertIgnore {
      it[legekontorId] = helsePersonell.legekontorId.value
      it[hpr] = helsePersonell.hpr.value
      it[navn] = helsePersonell.navn
      it[autorisasjon] = helsePersonell.autorisasjon
    }
  }

  suspend fun findByHpr(hpr: HelsepersonellHpr) = dbQuery {
    logger.info("looking up helsepersonell: $hpr")
    HelsepersonellTable.selectAll()
      .where { HelsepersonellTable.hpr eq hpr.value }
      .singleOrNull()
      ?.toHelsepersonell()
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun ResultRow.toHelsepersonell() =
    Helsepersonell(
      hpr = HelsepersonellHpr(this[HelsepersonellTable.hpr]),
      legekontorId = LegekontorId(this[HelsepersonellTable.legekontorId]),
      navn = this[HelsepersonellTable.navn],
      autorisasjon = this[HelsepersonellTable.autorisasjon],
    )
}
