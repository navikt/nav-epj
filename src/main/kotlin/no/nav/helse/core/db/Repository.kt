package no.nav.helse.core.db

import no.nav.helse.core.utils.logger
import no.nav.helse.epj.Pasient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

class Repository {
  private val logger = logger()

  suspend fun getPasienter(): List<Pasient> = dbQuery {
    PasientTable.selectAll().map { it.toPasient() }
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insertPasient(pasient: Pasient) = dbQuery {
    transaction {
      PasientTable.deleteAll()
      PasientTable.insert {
        it[id] = pasient.id
        it[legekontorId] = pasient.legekontorId
        it[fastlegeId] = pasient.fastlegeId
        it[navn] = pasient.navn
      }
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun ResultRow.toPasient() =
    Pasient(
      id = this[PasientTable.id],
      legekontorId = this[PasientTable.legekontorId],
      fastlegeId = this[PasientTable.fastlegeId],
      navn = this[PasientTable.navn],
    )
}
