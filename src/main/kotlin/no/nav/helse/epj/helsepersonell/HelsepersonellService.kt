package no.nav.helse.epj.helsepersonell

import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.legekontor.Legekontor

class HelsepersonellService(val helsepersonellRepository: HelsepersonellRepository) {
  val log = logger()

  suspend fun insertHelsepersonell(helsepersonell: OpprettHelsepersonell): Boolean {
    val insertHelsepersonell = helsepersonellRepository.insert(helsepersonell)
    log.info("inserted count: ${insertHelsepersonell.insertedCount}")
    return (insertHelsepersonell.insertedCount == 1)
  }

  suspend fun getHelsepersonell(hpr: HelsepersonellHpr): Helsepersonell? {
    return helsepersonellRepository.findByHpr(hpr)
  }

  suspend fun findOrCreateHelsepersonell(hpr: HelsepersonellHpr, navn: String): Helsepersonell {
    val helsepersonell = getHelsepersonell(hpr)
    if (helsepersonell != null) {
      return helsepersonell
    }
    val opprettHelsepersonell =
      OpprettHelsepersonell(
        legekontorId = Legekontor.DEFAULT.id,
        hpr = hpr,
        navn = navn,
        autorisasjon = "Lege", // TODO hent fra UserInfo
      )
    val insertHelsepersonell = insertHelsepersonell(opprettHelsepersonell)
    if (insertHelsepersonell) {
      return getHelsepersonell(hpr) ?: throw IllegalStateException("Helspersonell ikke funnet")
    }
    throw HelsepersonellNotFoundException(hpr)
  }
}
