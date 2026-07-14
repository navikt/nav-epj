package no.nav.helse.epj.db

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Pasient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

@OptIn(ExperimentalUuidApi::class)
class PasientRepository {

  private val logger = logger()

  suspend fun getPasient(id: String) = dbQuery {
    logger.info("Looking up pasient: $id")
    PasientTable.selectAll().where { PasientTable.id eq Uuid.parse(id) }.singleOrNull()?.toPasient()
  }

  suspend fun getPasientByFnr(fnr: String) = dbQuery {
    logger.info("Looking up pasient by fnr")
    PasientTable.selectAll().where { PasientTable.fnr eq fnr }.singleOrNull()?.toPasient()
  }

  suspend fun getPasienterByFastlege(fastlegeId: String) = dbQuery {
    logger.info("Looking up pasients for fastlegeId: $fastlegeId")
    PasientTable.selectAll()
      .where { PasientTable.fastlegeId eq Uuid.parse(fastlegeId) }
      .map { it.toPasient() }
  }

  suspend fun insertPasient(pasient: Pasient) = dbQuery {
    logger.info("Inserting pasient: ${pasient.navn}")
    PasientTable.insertIgnore {
      it[id] = Uuid.parse(pasient.id)
      it[legekontorId] = Uuid.parse(pasient.legekontorId)
      it[fastlegeId] = Uuid.parse(pasient.fastlegeId)
      it[navn] = pasient.navn
      it[fnr] = pasient.fnr
    }
  }

  private fun ResultRow.toPasient() =
    Pasient(
      id = this[PasientTable.id].toString(),
      legekontorId = this[PasientTable.legekontorId].toString(),
      fastlegeId = this[PasientTable.fastlegeId].toString(),
      navn = this[PasientTable.navn],
      fnr = this[PasientTable.fnr],
    )
}
