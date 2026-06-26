package no.nav.helse.epj.db

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.OpprettKonsultasjon
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

@OptIn(ExperimentalUuidApi::class)
class KonsultasjonRepository {
  private val logger = logger()

  suspend fun createKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon) = dbQuery {
    logger.info("creating konsultasjon: ${opprettKonsultasjon.pasientId}")
    KonsultasjonTable.insert {
      it[pasientId] = Uuid.parse(opprettKonsultasjon.pasientId)
      it[helsepersonellId] = Uuid.parse(opprettKonsultasjon.helsepersonellId)
      it[startetTidspunkt] = opprettKonsultasjon.startetTidspunkt
      it[problemstilling] = null
      it[type] = opprettKonsultasjon.type
      it[status] = opprettKonsultasjon.status
    }
  }

  suspend fun getAktivKonsultasjon(pasientId: String): Konsultasjon? = dbQuery {
    KonsultasjonTable.selectAll()
      .where {
        (KonsultasjonTable.pasientId eq Uuid.parse(pasientId)) and
          KonsultasjonTable.avsluttetTidspunkt.isNull()
      }
      .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
      .limit(1)
      .map { it.toKonsultasjon() }
      .singleOrNull()
  }

  private fun ResultRow.toKonsultasjon() =
    Konsultasjon(
      id = this[KonsultasjonTable.id].toString(),
      pasientId = this[KonsultasjonTable.pasientId].toString(),
      helsepersonellId = this[KonsultasjonTable.helsepersonellId].toString(),
      startetTidspunkt = this[KonsultasjonTable.startetTidspunkt],
      avsluttetTidspunkt = this[KonsultasjonTable.avsluttetTidspunkt],
      type = this[KonsultasjonTable.type],
      status = this[KonsultasjonTable.status],
      problemstilling = this[KonsultasjonTable.problemstilling],
      journalnotat = this[KonsultasjonTable.journalnotat],
    )
}
