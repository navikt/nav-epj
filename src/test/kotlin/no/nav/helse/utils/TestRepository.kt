package no.nav.helse.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.HelsepersonellTable
import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.Konsultasjon
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert

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
      it[helsepersonellId] = Uuid.parse(konsultasjon.helsepersonellId)
      it[startetTidspunkt] = konsultasjon.startetTidspunkt
      it[problemstilling] = null
      it[type] = konsultasjon.type
      it[status] = konsultasjon.status
    }
  }

  suspend fun deleteAllTestData() = dbQuery {
    DiagnoseTable.deleteAll()
    KonsultasjonTable.deleteAll()
    PasientTable.deleteAll()
    HelsepersonellTable.deleteAll()
  }
}
