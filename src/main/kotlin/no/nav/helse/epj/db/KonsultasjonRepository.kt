package no.nav.helse.epj.db

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.OppdaterKonsultasjonRequest
import no.nav.helse.epj.api.OpprettDiagnoseRequest
import no.nav.helse.epj.api.OpprettKonsultasjon
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

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

  suspend fun getKonsultasjoner(pasientId: String): List<Konsultasjon> = dbQuery {
    KonsultasjonTable.selectAll()
      .where { (KonsultasjonTable.pasientId eq Uuid.parse(pasientId)) }
      .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
      .map { it.toKonsultasjon() }
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

  suspend fun getKonsultasjon(id: String): Konsultasjon? {
    val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return null
    return dbQuery {
      KonsultasjonTable.selectAll()
        .where { KonsultasjonTable.id eq uuid }
        .map { it.toKonsultasjon() }
        .singleOrNull()
    }
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

  suspend fun oppdaterKonsultasjon(
    oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
    pasientId: String,
  ): Int = dbQuery {
    logger.info("update konsultasjon ${oppdaterKonsultasjon.konsultasjonId}")
    val updatedRows =
      KonsultasjonTable.update({
        (KonsultasjonTable.id eq Uuid.parse(oppdaterKonsultasjon.konsultasjonId)) and
          (KonsultasjonTable.pasientId eq Uuid.parse(pasientId))
      }) {
        it[journalnotat] = oppdaterKonsultasjon.journalNotat
      }
    if (updatedRows != 1) {
      return@dbQuery updatedRows
    }

    oppdaterKonsultasjon.diagnoser.forEach { diagnose ->
      insertDiagnoseIfNotExists(
        diagnose = diagnose,
        konsultasjonId = oppdaterKonsultasjon.konsultasjonId,
      )
    }

    if (oppdaterKonsultasjon.ferdigstill) {
      ferdigstillKonsultasjon(oppdaterKonsultasjon.konsultasjonId, pasientId)
    }
    updatedRows
  }

  private fun insertDiagnoseIfNotExists(
    diagnose: OpprettDiagnoseRequest,
    konsultasjonId: String,
  ): Int {
    val exists =
      DiagnoseTable.selectAll()
        .where {
          (DiagnoseTable.konsultasjonId eq Uuid.parse(konsultasjonId)) and
            (DiagnoseTable.diagnosekode eq diagnose.kode) and
            (DiagnoseTable.diagnosesystem eq diagnose.system)
        }
        .limit(1)
        .any()

    if (exists) {
      logger.info("Diagnose finnes allerede: ${diagnose.kode}")
      return 0
    }
    DiagnoseTable.insert {
      it[DiagnoseTable.konsultasjonId] = Uuid.parse(konsultasjonId)
      it[diagnosekode] = diagnose.kode
      it[diagnosesystem] = diagnose.system
    }
    return 1
  }

  private fun ferdigstillKonsultasjon(konsultasjonId: String, pasientId: String) {
    KonsultasjonTable.update({
      (KonsultasjonTable.id eq Uuid.parse(konsultasjonId)) and
        (KonsultasjonTable.pasientId eq Uuid.parse(pasientId))
    }) {
      it[avsluttetTidspunkt] = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      it[status] = "ferdigstilt"
    }
  }
}
