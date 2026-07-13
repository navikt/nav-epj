package no.nav.helse.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.HelsepersonellTable
import no.nav.helse.core.db.KonsultasjonHelsepersonell
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.OpprettKonsultasjon
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning

abstract class TestRepository : WithPostgresql() {
  init {
    runMigrations(true)
    connect()
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insert(helsePersonell: Helsepersonell) = dbQuery {
    HelsepersonellTable.insert {
      it[id] = Uuid.parse(helsePersonell.id)
      it[legekontorId] = Uuid.parse(helsePersonell.legekontorId)
      it[hpr] = helsePersonell.hpr
      it[navn] = helsePersonell.navn
      it[autorisasjon] = helsePersonell.autorisasjon
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insert(konsultasjon: Konsultasjon) = dbQuery {
    KonsultasjonTable.insert {
      it[id] = Uuid.parse(konsultasjon.id)
      it[pasientId] = Uuid.parse(konsultasjon.pasientId)
      it[startetTidspunkt] = konsultasjon.startetTidspunkt
      it[problemstilling] = null
      it[status] = konsultasjon.status
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insert(opprettKonsultasjon: OpprettKonsultasjon, tulleId: Uuid) = dbQuery {
    val konsultasjon =
      KonsultasjonTable.insertReturning {
          it[id] = tulleId
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

  suspend fun deleteAllTestData() = dbQuery {
    DiagnoseTable.deleteAll()
    KonsultasjonTable.deleteAll()
    PasientTable.deleteAll()
    HelsepersonellTable.deleteAll()
  }
}
