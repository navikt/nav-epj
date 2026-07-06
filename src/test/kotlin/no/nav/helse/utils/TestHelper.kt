package no.nav.helse.utils

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.Pasient
import no.nav.helse.epj.db.PasientRepository

const val LEGEKONTOR_ID = "a1000000-0000-0000-0000-000000000001"

class TestHelper : TestRepository() {

  val pasientRepository = PasientRepository()

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insertHelsepersonellOgPasientTestData(pasientId: String, legeId: String) {
    val helsepersonell =
      Helsepersonell(
        id = legeId,
        legekontorId = LEGEKONTOR_ID,
        hpr = "hpr",
        navn = "Legem",
        autorisasjon = "lege",
      )
    insert(helsepersonell)
    val pasient =
      Pasient(id = pasientId, legekontorId = LEGEKONTOR_ID, fastlegeId = legeId, navn = "Pasient")
    pasientRepository.insertPasient(pasient)
  }

  suspend fun insertPasientTestdata(pasientId: String, legeId: String) {
    val pasient =
      Pasient(id = pasientId, legekontorId = LEGEKONTOR_ID, fastlegeId = legeId, navn = "Pasient")
    pasientRepository.insertPasient(pasient)
  }

  suspend fun insertKonsultasjon(konsultasjonId: String, legeId: String, pasientId: String) =
    dbQuery {
      val konsultasjon =
        Konsultasjon(
          id = konsultasjonId,
          pasientId = pasientId,
          helsepersonellId = legeId,
          startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
          avsluttetTidspunkt = null,
          type = "pågående",
          status = "",
          problemstilling = null,
          journalnotat = null,
        )
      insert(konsultasjon)
    }
}
