package no.nav.helse.epj.db

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.Pasient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

@OptIn(ExperimentalUuidApi::class)
class PasientRepository {

  private val logger = logger()

  suspend fun getPasient(id: String) = dbQuery {
    logger.info("Looking up pasient: $id")
    PasientTable.selectAll().where { PasientTable.id eq Uuid.parse(id) }.singleOrNull()?.toPasient()
  }

  suspend fun getAllPasients() = dbQuery {
    logger.info("Looking up all pasients")
    PasientTable.selectAll().map { it.toPasient() }
  }

  suspend fun insertPasient(pasient: Pasient) = dbQuery {
    logger.info("Inserting pasient: $pasient")
    PasientTable.insert {
      it[id] = Uuid.parse(pasient.id)
      it[legekontorId] = Uuid.parse(pasient.legekontorId)
      it[fastlegeId] = Uuid.parse(pasient.fastlegeId)
      it[navn] = pasient.navn
    }
  }

  /*suspend fun createKonsultasjon(pasientId: String): Konsultasjon = dbQuery {
      KonsultasjonTable.insert {
        it[pasientId] = Uuid.parse(pasientId)
        it[helsepersonellId] =
        it[startetTidspunkt]
        it[avsluttetTidspunkt]
        it[type]
        it[status]
        it[problemstilling]
        it[journalnotat]


      }
    }
  */

  /*
    suspend fun getKonsultasjon(pasientId: String): List<Konsultasjon> = dbQuery {
      KonsultasjonTable
        .selectAll()
        .where { KonsultasjonTable.pasientId eq Uuid.parse(pasientId) }
        .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
        .map { it.toKonsultasjon() }
    }
  */

  private fun ResultRow.toPasient() =
    Pasient(
      id = this[PasientTable.id].toString(),
      legekontorId = this[PasientTable.legekontorId].toString(),
      fastlegeId = this[PasientTable.fastlegeId].toString(),
      navn = this[PasientTable.navn],
    )

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
