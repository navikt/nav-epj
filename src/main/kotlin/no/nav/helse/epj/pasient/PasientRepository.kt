package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

@OptIn(ExperimentalUuidApi::class)
class PasientRepository {

  private val logger = logger()

  suspend fun findById(id: Uuid) = dbQuery {
    logger.info("Looking up pasient by id: $id")
    PasientTable.selectAll().where { PasientTable.id eq id }.singleOrNull()?.toPasient()
  }

  suspend fun findByFnr(fnr: String) = dbQuery {
    logger.info("Looking up pasient by fnr")
    PasientTable.selectAll().where { PasientTable.fnr eq fnr }.singleOrNull()?.toPasient()
  }

  suspend fun findbyHpr(hpr: String) = dbQuery {
    logger.info("Looking up pasient by hpr: $hpr")
    PasientTable.selectAll().where { PasientTable.hpr eq hpr }.map { it.toPasient() }
  }

  suspend fun insert(pasient: Pasient) = dbQuery {
    logger.info("Inserting pasient: ${pasient.navn}")
    PasientTable.insertIgnore {
      it[id] = Uuid.parse(pasient.id.value.toString())
      it[legekontorId] = pasient.legekontorId.value
      it[hpr] = pasient.hpr.value
      it[navn] = pasient.navn
      it[fnr] = pasient.fnr
    }
  }

  private fun ResultRow.toPasient() =
    Pasient(
      id = PatientId(this[PasientTable.id]),
      legekontorId = Legekontor.DEFAULT.id,
      hpr = HelsepersonellHpr(PasientTable.hpr.toString()),
      navn = this[PasientTable.navn],
      fnr = this[PasientTable.fnr],
    )
}
