package no.nav.helse.epj.legekontor

import no.nav.helse.core.utils.LegekontorNotfoundException

class LegekontorService(val legekontorRepository: LegekontorRepository) {

  suspend fun getLegekontor(): Legekontor {
    return legekontorRepository.find() ?: throw LegekontorNotfoundException()
  }
}
