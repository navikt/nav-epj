package no.nav.helse.epj.db

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.KonsultasjonHelsepersonell
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Diagnose
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.KonsultasjonStatus
import no.nav.helse.epj.api.OppdaterKonsultasjonRequest
import no.nav.helse.epj.api.OpprettDiagnoseRequest
import no.nav.helse.epj.api.OpprettKonsultasjon
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

@OptIn(ExperimentalUuidApi::class)
class KonsultasjonRepository {
  private val logger = logger()

  suspend fun createKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon) = dbQuery {
    val konsultasjon =
      KonsultasjonTable.insertReturning {
          it[pasientId] = Uuid.parse(opprettKonsultasjon.pasientId)
          it[startetTidspunkt] = opprettKonsultasjon.startetTidspunkt
          it[status] = opprettKonsultasjon.status
        }
        .single()
    val id = konsultasjon[KonsultasjonTable.id]
    opprettKonsultasjon.hpr.forEach { hprValue ->
      KonsultasjonHelsepersonell.insert {
        it[konsultasjonId] = id
        it[hpr] = hprValue
      }
    }
    konsultasjon[KonsultasjonTable.id].toString()
  }

  suspend fun getKonsultasjoner(pasientId: String): List<Konsultasjon> = dbQuery {
    val konsultasjoner =
      KonsultasjonTable.selectAll()
        .where { (KonsultasjonTable.pasientId eq Uuid.parse(pasientId)) }
        .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
        .toList()

    val konsultasjonIder = konsultasjoner.map { it[KonsultasjonTable.id] }
    val hprByKonsultasjonId =
      KonsultasjonHelsepersonell.selectAll()
        .where { KonsultasjonHelsepersonell.konsultasjonId inList konsultasjonIder }
        .groupBy(
          keySelector = { it[KonsultasjonHelsepersonell.konsultasjonId] },
          valueTransform = { it[KonsultasjonHelsepersonell.hpr] },
        )

    konsultasjoner.map { row ->
      val konsultasjonId = row[KonsultasjonTable.id]
      val hprListe = hprByKonsultasjonId[konsultasjonId].orEmpty()
      row.toKonsultasjon(hprListe)
    }
  }

  suspend fun getAktivKonsultasjon(pasientId: String): Konsultasjon? {
    val pasientUuid = runCatching { Uuid.parse(pasientId) }.getOrNull() ?: return null
    return dbQuery {
      val konsultasjon =
        KonsultasjonTable.selectAll()
          .where {
            (KonsultasjonTable.pasientId eq pasientUuid) and
              KonsultasjonTable.avsluttetTidspunkt.isNull()
          }
          .orderBy(KonsultasjonTable.startetTidspunkt, SortOrder.DESC)
          .limit(1)
          .singleOrNull() ?: return@dbQuery null

      val hprListe =
        KonsultasjonHelsepersonell.select(KonsultasjonHelsepersonell.hpr)
          .where { KonsultasjonHelsepersonell.konsultasjonId eq konsultasjon[KonsultasjonTable.id] }
          .map { it[KonsultasjonHelsepersonell.hpr] }
      konsultasjon.toKonsultasjon(hprListe)
    }
  }

  suspend fun getKonsultasjon(id: String): Konsultasjon? {
    val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: return null
    return dbQuery {
      val konsultasjon =
        KonsultasjonTable.selectAll().where { KonsultasjonTable.id eq uuid }.singleOrNull()
          ?: return@dbQuery null

      val hprListe =
        KonsultasjonHelsepersonell.select(KonsultasjonHelsepersonell.hpr)
          .where { KonsultasjonHelsepersonell.konsultasjonId eq konsultasjon[KonsultasjonTable.id] }
          .map { it[KonsultasjonHelsepersonell.hpr] }
      konsultasjon.toKonsultasjon(hprListe)
    }
  }

  suspend fun getDiagnoser(konsultasjonId: String): List<Diagnose>? {
    val uuid = runCatching { Uuid.parse(konsultasjonId) }.getOrNull() ?: return null
    return dbQuery {
      DiagnoseTable.selectAll()
        .where { DiagnoseTable.konsultasjonId eq uuid }
        .map { it.toDiagnose() }
    }
  }

  private fun ResultRow.toDiagnose() =
    Diagnose(
      kode = this[DiagnoseTable.diagnosekode],
      system = this[DiagnoseTable.diagnosesystem],
      beskrivelse = this[DiagnoseTable.beskrivelse],
    )

  private fun ResultRow.toKonsultasjon(hprListe: List<String>): Konsultasjon =
    Konsultasjon(
      id = this[KonsultasjonTable.id].toString(),
      pasientId = this[KonsultasjonTable.pasientId].toString(),
      hpr = hprListe,
      startetTidspunkt = this[KonsultasjonTable.startetTidspunkt],
      avsluttetTidspunkt = this[KonsultasjonTable.avsluttetTidspunkt],
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

  suspend fun insertDiagnoseIfNotExists(
    diagnose: OpprettDiagnoseRequest,
    konsultasjonId: String,
  ): Int = dbQuery {
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
      return@dbQuery 0
    }
    DiagnoseTable.insert {
      it[DiagnoseTable.konsultasjonId] = Uuid.parse(konsultasjonId)
      it[diagnosekode] = diagnose.kode
      it[diagnosesystem] = diagnose.system
      it[beskrivelse] = diagnose.beskrivelse
    }
    1
  }

  private fun ferdigstillKonsultasjon(konsultasjonId: String, pasientId: String) {
    KonsultasjonTable.update({
      (KonsultasjonTable.id eq Uuid.parse(konsultasjonId)) and
        (KonsultasjonTable.pasientId eq Uuid.parse(pasientId))
    }) {
      it[avsluttetTidspunkt] = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      it[status] = KonsultasjonStatus.FULLFØRT
    }
  }
}
