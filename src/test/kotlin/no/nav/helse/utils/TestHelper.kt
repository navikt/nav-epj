package no.nav.helse.utils

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.OpprettKonsultasjon
import no.nav.helse.epj.api.Pasient
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository

const val LEGEKONTOR_ID = "a1000000-0000-0000-0000-000000000001"

class TestHelper : TestRepository() {

  val pasientRepository = PasientRepository()
  val konsultasjonRepository = KonsultasjonRepository()

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insertHelsepersonellOgPasientTestData(pasientId: String, legeId: String) {
    val helsepersonell =
      Helsepersonell(
        id = legeId,
        legekontorId = LEGEKONTOR_ID,
        hpr = "hpr",
        herId = "her-id",
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

  suspend fun createKonsultasjon(legeId: String, pasientId: String) {
    val konsultasjon =
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf(legeId),
        startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        type = "pågående",
        status = "",
      )
    konsultasjonRepository.createKonsultasjon(konsultasjon)
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun insertKonsultasjonWithPredefinedId(
    legeId: String,
    pasientId: String,
    konsultasjonId: Uuid,
  ) {
    val konsultasjon =
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf(legeId),
        startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        type = "pågående",
        status = "",
      )
    insert(konsultasjon, konsultasjonId)
  }
}
