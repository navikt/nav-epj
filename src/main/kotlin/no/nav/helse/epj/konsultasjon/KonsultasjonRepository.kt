package no.nav.helse.epj.konsultasjon

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
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.pasient.PatientId
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

  suspend fun listByPasientId(id: PatientId): List<Konsultasjon> = dbQuery {
    val konsultasjoner =
      KonsultasjonTable.selectAll()
        .where { (KonsultasjonTable.pasientId eq id.value) }
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

    val diagnoser =
      DiagnoseTable.selectAll()
        .where { DiagnoseTable.konsultasjonId inList konsultasjonIder }
        .groupBy(
          keySelector = { it[DiagnoseTable.konsultasjonId] },
          valueTransform = { it.toDiagnose() },
        )

    konsultasjoner.map { row ->
      val konsultasjonId = row[KonsultasjonTable.id]
      val hprListe = hprByKonsultasjonId[konsultasjonId].orEmpty()
      val journalnotatListe = journalnotatByKonsultasjonId[konsultasjonId].orEmpty()
      val diagnoseListe = diagnoser[konsultasjonId].orEmpty()
      row.toKonsultasjonWithHprAndJournalnotat(hprListe, journalnotatListe, diagnoseListe)
    }
  }

  suspend fun insert(opprettKonsultasjon: OpprettKonsultasjon) = dbQuery {
    val konsultasjon =
      KonsultasjonTable.insertReturning {
          it[pasientId] = opprettKonsultasjon.pasientId.value
          it[startetTidspunkt] = opprettKonsultasjon.startetTidspunkt
          it[status] = opprettKonsultasjon.status
        }
        .single()
    val id = konsultasjon[KonsultasjonTable.id]
    opprettKonsultasjon.hpr.forEach { hprValue ->
      KonsultasjonHelsepersonell.insert {
        it[konsultasjonId] = id
        it[hpr] = hprValue.value
      }
    }
    KonsultasjonId(konsultasjon[KonsultasjonTable.id])
  }

  suspend fun findActiveByPasientId(pasientId: PatientId): Konsultasjon? {
    val pasientUuid = pasientId.value
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

  suspend fun findByKonsultasjonId(konsultasjonId: KonsultasjonId): Konsultasjon? {
    return dbQuery {
      val konsultasjon =
        KonsultasjonTable.selectAll()
          .where { KonsultasjonTable.id eq konsultasjonId.value }
          .singleOrNull() ?: return@dbQuery null
      toEpjKonsultasjon(konsultasjon)
    }
  }

  suspend fun update(oppdaterKonsultasjon: OppdaterKonsultasjonRequest, pasientId: PatientId): Int =
    dbQuery {
      logger.info("Oppdaterer konsultasjon {}", oppdaterKonsultasjon.konsultasjonId)

      val konsultasjonPasient =
        KonsultasjonTable.selectAll()
          .where {
            (KonsultasjonTable.id eq oppdaterKonsultasjon.konsultasjonId.value) and
              (KonsultasjonTable.pasientId eq pasientId.value)
          }
          .limit(1)
          .any()

      if (!konsultasjonPasient) {
        logger.warn(
          "Fant ikke konsultasjon {} for pasientId {}, avbryter oppdatering",
          oppdaterKonsultasjon.konsultasjonId,
          pasientId,
        )
        return@dbQuery 0
      }

      var updatedRows = 0

      oppdaterKonsultasjon.diagnoser.forEach { diagnose ->
        updatedRows +=
          updateDiagnose(
            diagnose = diagnose,
            konsultasjonId = oppdaterKonsultasjon.konsultasjonId.value,
          )
      }

      logger.info("Oppdaterert diagnosetabell med rows: {}", updatedRows)

      oppdaterKonsultasjon.journalNotat?.let { journalnotat ->
        updatedRows +=
          updateJournalnotat(
            konsultasjonId = oppdaterKonsultasjon.konsultasjonId,
            pasientId = pasientId,
            journalnotat = journalnotat,
          )
      }

      logger.info("Oppdaterert journalnotattable med rows: {}", updatedRows)

      if (oppdaterKonsultasjon.ferdigstill) {
        updatedRows +=
          ferdigstill(konsultasjonId = oppdaterKonsultasjon.konsultasjonId, pasientId = pasientId)
      }

      logger.info(
        "totalt oppdater diagnose, journalnotat og konsultasjontable med rows: {}",
        updatedRows,
      )

      updatedRows
    }

  private fun ferdigstill(konsultasjonId: KonsultasjonId, pasientId: PatientId): Int =
    KonsultasjonTable.update({
      (KonsultasjonTable.id eq konsultasjonId.value) and
        (KonsultasjonTable.pasientId eq pasientId.value)
    }) {
      it[avsluttetTidspunkt] = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      it[status] = KonsultasjonStatus.FULLFØRT
    }

  suspend fun updateJournalnotat(
    konsultasjonId: KonsultasjonId,
    pasientId: PatientId,
    journalnotat: String,
  ): Int = dbQuery {
    JournalnotatTable.insert {
        it[JournalnotatTable.konsultasjonId] = konsultasjonId.value
        it[JournalnotatTable.pasientId] = pasientId.value
        it[JournalnotatTable.journalnotat] = journalnotat
      }
      .insertedCount
  }

  suspend fun updateDiagnose(diagnose: OpprettDiagnoseRequest, konsultasjonId: Uuid): Int =
    dbQuery {
      val kodeverkDiagnose =
        no.nav.tsm.diagnoser.Diagnose.from(diagnose.system.toString(), diagnose.kode)
          ?: throw UgyldigDiagnoseException(diagnose.kode, diagnose.system.toString())

      val exists =
        DiagnoseTable.selectAll()
          .where {
            (DiagnoseTable.konsultasjonId eq konsultasjonId) and
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
        it[DiagnoseTable.konsultasjonId] = konsultasjonId
        it[diagnosekode] = diagnose.kode
        it[diagnosesystem] = diagnose.system.toString()
        it[beskrivelse] = kodeverkDiagnose.text
      }
      1
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

    val diagnoseListe =
      DiagnoseTable.selectAll()
        .where { (DiagnoseTable.konsultasjonId eq konsultasjon[KonsultasjonTable.id]) }
        .map { it.toDiagnose() }

    return konsultasjon.toKonsultasjonWithHprAndJournalnotat(
      hprListe,
      journalnotatListe,
      diagnoseListe,
    )
  }

  @OptIn(ExperimentalUuidApi::class)
  fun ResultRow.toJournalnotat(): Journalnotat =
    Journalnotat(
      id = JournalnotatId(this[JournalnotatTable.id]),
      konsultasjonId = KonsultasjonId(this[JournalnotatTable.konsultasjonId]),
      pasientId = PatientId(this[JournalnotatTable.pasientId]),
      journalnotat = this[JournalnotatTable.journalnotat],
    )

  @OptIn(ExperimentalUuidApi::class)
  fun ResultRow.toKonsultasjonWithHprAndJournalnotat(
    hprListe: List<String>,
    journalnotatListe: List<Journalnotat>,
    diagnoseListe: List<Diagnose>,
  ): Konsultasjon =
    Konsultasjon(
      id = KonsultasjonId(this[KonsultasjonTable.id]),
      pasientId = PatientId(this[KonsultasjonTable.pasientId]),
      hpr = hprListe,
      startetTidspunkt = this[KonsultasjonTable.startetTidspunkt],
      avsluttetTidspunkt = this[KonsultasjonTable.avsluttetTidspunkt],
      status = this[KonsultasjonTable.status],
      problemstilling = this[KonsultasjonTable.problemstilling],
      journalnotat = journalnotatListe,
      diagnoser = diagnoseListe,
    )

  fun ResultRow.toDiagnose() =
    Diagnose(
      kode = this[DiagnoseTable.diagnosekode],
      system = DiagnoseSystem.valueOf(this[DiagnoseTable.diagnosesystem]),
      beskrivelse = this[DiagnoseTable.beskrivelse],
    )
}
