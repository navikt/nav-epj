package no.nav.helse.epj

import no.nav.helse.core.utils.logger
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.OpprettHelsepersonell
import no.nav.helse.epj.api.Pasient
import no.nav.helse.epj.api.legekontorId
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.PasientRepository
import no.nav.helse.helseIdAuth.User

class EpjService(
  private val pasientRepository: PasientRepository,
  private val helsepersonellRepository: HelsepersonellRepository,
) {

  private val logger = logger()

  suspend fun getPasienter(): List<Pasient> {
    return pasientRepository.getAllPasients()
  }

  suspend fun getPasient(id: String): Pasient? {
    return pasientRepository.getPasient(id)
  }

  /*  suspend fun getKonsultasjon(id: String): Konsultasjon {
    repository.getKonsultasjon(id)
  }*/

  /*fun createKonsultasjon(pasientId: String): Konsultasjon {
    return repository.createKonsultasjon(pasientId)
  }*/

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
        legekontorId = legekontorId,
        hpr = principal.hpr,
        navn = principal.name,
        autorisasjon = "Lege", // TODO: hent fra UserInfo
      )
    val insertHelsepersonell = insertHelsepersonell(opprettHelsepersonell)
    if (insertHelsepersonell) {
      return getHelspersonell(principal.hpr)
        ?: throw IllegalStateException("Helspersonell ikke funnet")
    }
    throw IllegalStateException("Helspersonell ikke funnet")
  }
}

/*
en pasient kan ha mange konsultasjoiner


 */
