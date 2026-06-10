package no.nav.helse.core.db

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.Pasient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class Repository {
  private val logger = logger()

  suspend fun getPasienter(): List<Pasient> = dbQuery {
    PasientTable.selectAll().map { it.toPasient() }
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun getPasient(id: String): Pasient = dbQuery {
    PasientTable.selectAll().where { PasientTable.id eq Uuid.parse(id) }.single().toPasient()
  }

  suspend fun deleteAllPasienter(): Int = dbQuery { PasientTable.deleteAll() }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insertPasient(pasient: Pasient) = dbQuery {
    PasientTable.insert {
      it[id] = pasient.id.toKotlinUuid()
      it[legekontorId] = pasient.legekontorId.toKotlinUuid()
      it[fastlegeId] = pasient.fastlegeId.toKotlinUuid()
      it[navn] = pasient.navn
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun ResultRow.toPasient() =
    Pasient(
      id = this[PasientTable.id].toJavaUuid(),
      legekontorId = this[PasientTable.legekontorId].toJavaUuid(),
      fastlegeId = this[PasientTable.fastlegeId].toJavaUuid(),
      navn = this[PasientTable.navn],
    )
}
