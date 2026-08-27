package no.nav.helse.epj.helsepersonell

import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.PasientId

class HelsepersonellService(val helsepersonellRepository: HelsepersonellRepository) {
  val log = logger()

  suspend fun insertHelsepersonell(helsepersonell: OpprettHelsepersonell): Boolean {
    val insertHelsepersonell = helsepersonellRepository.insert(helsepersonell)
    log.info("inserted count: ${insertHelsepersonell.insertedCount}")
    return (insertHelsepersonell.insertedCount == 1)
  }

  suspend fun getHelsepersonell(pasientId: PasientId): List<HelsepersonellHpr> {
    val hpr = helsepersonellRepository.listByPatientId(pasientId)
    return hpr
  }

  suspend fun getHelsepersonell(konsultasjonId: KonsultasjonId): String {
    val hpr =
      helsepersonellRepository.findByKonsultasjonId(konsultasjonId)
        ?: throw KonsultasjonNotFoundException(konsultasjonId)
    return hpr
  }

  suspend fun getHelsepersonell(hpr: HelsepersonellHpr): Helsepersonell {
    val helsepersonell =
      helsepersonellRepository.findByHpr(hpr) ?: throw HelsepersonellNotFoundException(hpr)
    return helsepersonell
  }

  suspend fun findOrCreateHelsepersonell(hpr: HelsepersonellHpr, navn: String): Helsepersonell {
    val helsepersonell = helsepersonellRepository.findByHpr(hpr)

    if (helsepersonell == null) {
      log.info("Fant ikke helsepersonell for hpr $hpr, oppretter ny")
      val opprettHelsepersonell =
        OpprettHelsepersonell(
          legekontorId = Legekontor.DEFAULT.id,
          hpr = hpr,
          navn = navn,
          autorisasjon = "Lege", // TODO hent fra UserInfo
        )
      insertHelsepersonell(opprettHelsepersonell)
      return getHelsepersonell(hpr)
    }
    return helsepersonell
  }
}
