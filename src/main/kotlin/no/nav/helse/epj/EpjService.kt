package no.nav.helse.epj

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.LEGEKONTOR_ID
import no.nav.helse.epj.api.OppdaterKonsultasjonRequest
import no.nav.helse.epj.api.OpprettHelsepersonell
import no.nav.helse.epj.api.OpprettKonsultasjon
import no.nav.helse.epj.api.Pasient
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository
import no.nav.helse.helseIdAuth.User

class EpjService(
  private val pasientRepository: PasientRepository,
  private val helsepersonellRepository: HelsepersonellRepository,
  private val konsultasjonRepository: KonsultasjonRepository,
) {

  private val logger = logger()

  suspend fun getPasienter(): List<Pasient> {
    return pasientRepository.getAllPatients()
  }

  suspend fun getPasient(id: String): Pasient? {
    return pasientRepository.getPasient(id)
  }

  suspend fun getKonsultasjon(id: String): Konsultasjon? {
    return konsultasjonRepository.getKonsultasjon(id)
  }

  suspend fun getKonsultasjoner(pasientId: String): List<Konsultasjon> {
    return konsultasjonRepository.getKonsultasjoner(pasientId)
  }

  suspend fun getAktivKonsultasjon(pasientId: String): Konsultasjon? {
    return konsultasjonRepository.getAktivKonsultasjon(pasientId)
  }

  suspend fun createKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon): Konsultasjon {
    val createdId = konsultasjonRepository.createKonsultasjon(opprettKonsultasjon)
    val createdKonsultasjon =
      konsultasjonRepository.getKonsultasjon(createdId)
        ?: throw IllegalStateException("Konsultasjon ble ikke opprettet")

    logger.info(
      "Created konsultasjon id={} for pasientId={}",
      createdKonsultasjon.id,
      opprettKonsultasjon.pasientId,
    )
    return createdKonsultasjon
  }

  suspend fun getOrCreateKonsultasjon(pasientId: String, hpr: String): Konsultasjon {
    val aktivKonsultasjon = getAktivKonsultasjon(pasientId)
    if (aktivKonsultasjon != null) return aktivKonsultasjon
    val opprettKonsultasjon =
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf(hpr), // TODO: send inn liste med hpr i funksjonen - ikke kun en
        startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        type = "fysisk",
        status = "pågående",
      )
    return createKonsultasjon(opprettKonsultasjon)
  }

  suspend fun insertHelsepersonell(helsepersonell: OpprettHelsepersonell): Boolean {
    val insertHelsepersonell = helsepersonellRepository.insertHelsepersonell(helsepersonell)
    logger.info("inserted count: ${insertHelsepersonell.insertedCount}")
    return (insertHelsepersonell.insertedCount == 1)
  }

  suspend fun getHelspersonell(hpr: String): Helsepersonell? {
    return helsepersonellRepository.getHelsepersonell(hpr)
  }

  suspend fun findOrCreateHelsepersonell(principal: User): Helsepersonell {
    val helsepersonell = getHelspersonell(principal.hpr)
    if (helsepersonell != null) {
      return helsepersonell
    }
    val opprettHelsepersonell =
      OpprettHelsepersonell(
        legekontorId = LEGEKONTOR_ID,
        hpr = principal.hpr,
        navn = principal.name,
        autorisasjon = "Lege", // TODO hent fra UserInfo
      )
    val insertHelsepersonell = insertHelsepersonell(opprettHelsepersonell)
    if (insertHelsepersonell) {
      return getHelspersonell(principal.hpr)
        ?: throw IllegalStateException("Helspersonell ikke funnet")
    }
    throw IllegalStateException("Helspersonell ikke funnet")
  }

  suspend fun oppdaterKonsultasjon(
    oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
    pasientId: String,
  ) {
    logger.info("oppdater konsultasjon på pasientId: $pasientId")
    val updatedRows = konsultasjonRepository.oppdaterKonsultasjon(oppdaterKonsultasjon, pasientId)
    if (updatedRows != 1) {
      throw IllegalStateException(
        "Fant ikke konsultasjon med id=${oppdaterKonsultasjon.konsultasjonId} på pasientId: $pasientId"
      )
    }
  }
}
