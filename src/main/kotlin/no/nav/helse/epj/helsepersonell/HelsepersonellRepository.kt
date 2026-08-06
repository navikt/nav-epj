package no.nav.helse.epj.helsepersonell

import no.nav.helse.core.db.HelsepersonellTable
import no.nav.helse.core.db.KonsultasjonHelsepersonell
import no.nav.helse.core.db.PasientHelsepersonell
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.pasient.PatientId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

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

  suspend fun listByPatientId(patientId: PatientId): List<HelsepersonellHpr> = dbQuery {
    logger.info("Looking up helsepersonell on patientId: ${patientId.value}")

    PasientHelsepersonell.select(PasientHelsepersonell.hpr)
      .where { PasientHelsepersonell.pasientId eq patientId.value }
      .map { row -> HelsepersonellHpr(row[PasientHelsepersonell.hpr]) }
  }

  suspend fun findByHpr(hpr: HelsepersonellHpr) = dbQuery {
    logger.info("looking up helsepersonell: $hpr")
    HelsepersonellTable.selectAll()
      .where { HelsepersonellTable.hpr eq hpr.value }
      .singleOrNull()
      ?.toHelsepersonell()
  }

  suspend fun findByKonsultasjonId(konsultasjonId: KonsultasjonId): String? = dbQuery {
    logger.info("Looking up helsepersonell by konsultasjonId: ${konsultasjonId.value}")

    KonsultasjonHelsepersonell.select(KonsultasjonHelsepersonell.hpr)
      .where { KonsultasjonHelsepersonell.konsultasjonId eq konsultasjonId.value }
      .singleOrNull()
      ?.get(KonsultasjonHelsepersonell.hpr)
  }

  private fun ResultRow.toHelsepersonell() =
    Helsepersonell(
      hpr = HelsepersonellHpr(this[HelsepersonellTable.hpr]),
      legekontorId = LegekontorId(this[HelsepersonellTable.legekontorId]),
      navn = this[HelsepersonellTable.navn],
      autorisasjon = this[HelsepersonellTable.autorisasjon],
    )
}
