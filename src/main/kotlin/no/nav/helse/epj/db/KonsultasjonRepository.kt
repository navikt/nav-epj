package no.nav.helse.epj.db

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.JournalnotatTable
import no.nav.helse.core.db.KonsultasjonHelsepersonell
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.diagnose.lookupDiagnose
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Diagnose
import no.nav.helse.epj.api.Journalnotat
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

  suspend fun insertKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon) = dbQuery {
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

  suspend fun getPasientKonsultasjoner(pasientId: String): List<Konsultasjon> = dbQuery {
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

    val journalnotatByKonsultasjonId =
      JournalnotatTable.selectAll()
        .where { JournalnotatTable.konsultasjonId inList konsultasjonIder }
        .groupBy(
          keySelector = { it[JournalnotatTable.konsultasjonId] },
          valueTransform = { it.toJournalnotat() },
        )

    konsultasjoner.map { row ->
      val konsultasjonId = row[KonsultasjonTable.id]
      val hprListe = hprByKonsultasjonId[konsultasjonId].orEmpty()
      val journalnotatListe = journalnotatByKonsultasjonId[konsultasjonId].orEmpty()
      row.toKonsultasjon(hprListe, journalnotatListe)
    }
  }

  suspend fun getPasientAktivKonsultasjon(pasientId: String): Konsultasjon? {
    val pasientUuid = Uuid.parse(pasientId)
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
      toEpjKonsultasjon(konsultasjon)
    }
  }

  suspend fun getKonsultasjon(konsultasjonId: String): Konsultasjon? {
    val uuid = Uuid.parse(konsultasjonId)
    return dbQuery {
      val konsultasjon =
        KonsultasjonTable.selectAll().where { KonsultasjonTable.id eq uuid }.singleOrNull()
          ?: return@dbQuery null
      toEpjKonsultasjon(konsultasjon)
    }
  }

  suspend fun hasJournalnotat(id: String): Boolean {
    val uuid = Uuid.parse(id)
    val journalnotat = dbQuery {
      JournalnotatTable.selectAll()
        .where { JournalnotatTable.id eq uuid }
        .singleOrNull()
        ?.toJournalnotat()
    }
    return journalnotat != null
  }

  suspend fun getAllJournalnotat(pasientId: String): Journalnotat? {
    val uuid = Uuid.parse(pasientId)
    return dbQuery {
      JournalnotatTable.selectAll()
        .where { JournalnotatTable.pasientId eq uuid }
        .singleOrNull()
        ?.toJournalnotat()
    }
  }

  suspend fun getDiagnoser(konsultasjonId: String): List<Diagnose> {
    val uuid = Uuid.parse(konsultasjonId)
    return dbQuery {
      DiagnoseTable.selectAll()
        .where { DiagnoseTable.konsultasjonId eq uuid }
        .map { it.toDiagnose() }
    }
  }

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
    val kodeverkDiagnose =
      lookupDiagnose(diagnose.system, diagnose.kode)
        ?: throw UgyldigDiagnoseException(diagnose.kode, diagnose.system.toString())

    val exists =
      DiagnoseTable.selectAll()
        .where {
          (DiagnoseTable.konsultasjonId eq Uuid.parse(konsultasjonId)) and
            (DiagnoseTable.diagnosekode eq diagnose.kode) and
            (DiagnoseTable.diagnosesystem eq diagnose.system.toString())
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
      it[diagnosesystem] = diagnose.system.toString()
      it[beskrivelse] = kodeverkDiagnose.text
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

  private fun toEpjKonsultasjon(konsultasjon: ResultRow): Konsultasjon {
    val hprListe =
      KonsultasjonHelsepersonell.select(KonsultasjonHelsepersonell.hpr)
        .where { KonsultasjonHelsepersonell.konsultasjonId eq konsultasjon[KonsultasjonTable.id] }
        .map { it[KonsultasjonHelsepersonell.hpr] }

    val journalnotatListe =
      JournalnotatTable.selectAll()
        .where { (JournalnotatTable.konsultasjonId eq konsultasjon[KonsultasjonTable.id]) }
        .map { it.toJournalnotat() }

    return konsultasjon.toKonsultasjon(hprListe, journalnotatListe)
  }

}
