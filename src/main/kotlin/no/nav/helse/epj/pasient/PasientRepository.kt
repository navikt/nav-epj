package no.nav.helse.epj.pasient

import kotlin.collections.map
import kotlin.uuid.Uuid
import no.nav.helse.core.db.PasientHelsepersonell
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class PasientRepository {

  private val logger = logger()

  suspend fun findById(id: Uuid) = dbQuery {
    logger.info("Looking up pasient by id: $id")
    val patient = PasientTable.selectAll().where { PasientTable.id eq id }.singleOrNull()
    val hpr =
      PasientHelsepersonell.select(PasientHelsepersonell.hpr)
        .where { PasientHelsepersonell.pasientId eq id }
        .map { row -> HelsepersonellHpr(row[PasientHelsepersonell.hpr]) }

    patient?.toPasient(hpr)
  }

  suspend fun findByFnr(fnr: String) = dbQuery {
    logger.info("Looking up pasient by fnr")
    val patient = PasientTable.selectAll().where { PasientTable.fnr eq fnr }.singleOrNull()
    val patientId = patient?.get(PasientTable.id) ?: return@dbQuery null

    val hpr =
      PasientHelsepersonell.select(PasientHelsepersonell.hpr)
        .where { PasientHelsepersonell.pasientId eq patientId }
        .map { row -> HelsepersonellHpr(row[PasientHelsepersonell.hpr]) }

    patient.toPasient(hpr)
  }

  suspend fun listByHpr(hpr: HelsepersonellHpr): List<Pasient> = dbQuery {
    logger.info("Looking up pasient by hpr: $hpr")

    val patientIds =
      PasientHelsepersonell.select(PasientHelsepersonell.pasientId)
        .where { PasientHelsepersonell.hpr eq hpr.value }
        .map { row -> row[PasientHelsepersonell.pasientId] }
        .distinct()

    if (patientIds.isEmpty()) {
      return@dbQuery emptyList()
    }

    val hprByPatientId =
      PasientHelsepersonell.select(PasientHelsepersonell.pasientId, PasientHelsepersonell.hpr)
        .where { PasientHelsepersonell.pasientId inList patientIds }
        .groupBy(
          keySelector = { row -> row[PasientHelsepersonell.pasientId] },
          valueTransform = { row -> HelsepersonellHpr(row[PasientHelsepersonell.hpr]) },
        )

    PasientTable.selectAll()
      .where { PasientTable.id inList patientIds }
      .map { row ->
        val patientId = row[PasientTable.id]

        row.toPasient(hpr = hprByPatientId[patientId].orEmpty())
      }
  }

  suspend fun insert(pasient: Pasient) = dbQuery {
    logger.info("Inserting pasient: ${pasient.navn}")

    PasientTable.insertIgnore {
      it[id] = pasient.id.value
      it[legekontorId] = pasient.legekontorId.value
      it[navn] = pasient.navn
      it[fnr] = pasient.fnr
    }

    pasient.hprNumbers.forEach {
      val value = it.value
      PasientHelsepersonell.insert {
        it[pasientId] = pasient.id.value
        it[PasientHelsepersonell.hpr] = value
      }
    }
  }

  private fun ResultRow.toPasient(hpr: List<HelsepersonellHpr>): Pasient =
    Pasient(
      id = PatientId(this[PasientTable.id]),
      legekontorId = Legekontor.DEFAULT.id,
      hprNumbers = hpr,
      navn = this[PasientTable.navn],
      fnr = this[PasientTable.fnr],
    )
}
