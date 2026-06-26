package no.nav.helse.epj.db

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.HelsepersonellTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.OpprettHelsepersonell
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class HelsepersonellRepository {

  private val logger = logger()

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insertHelsepersonell(helsePersonell: OpprettHelsepersonell) = dbQuery {
    logger.info("Inserting helsepersonell: ${helsePersonell.navn}")
    HelsepersonellTable.insert {
      it[legekontorId] = Uuid.parse(helsePersonell.legekontorId)
      it[hpr] = helsePersonell.hpr
      it[navn] = helsePersonell.navn
      it[autorisasjon] = helsePersonell.autorisasjon
    }
  }

  suspend fun getHelsepersonell(hpr: String) = dbQuery {
    logger.info("looking up helsepersonell: $hpr")
    HelsepersonellTable.selectAll()
      .where { HelsepersonellTable.hpr eq hpr }
      .singleOrNull()
      ?.toHelsepersonell()
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun ResultRow.toHelsepersonell() =
    Helsepersonell(
      id = this[HelsepersonellTable.id].toString(),
      legekontorId = this[HelsepersonellTable.legekontorId].toString(),
      hpr = this[HelsepersonellTable.hpr],
      navn = this[HelsepersonellTable.navn],
      autorisasjon = this[HelsepersonellTable.autorisasjon],
    )
}
