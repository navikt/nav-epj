package no.nav.helse.epj.legekontor

import no.nav.helse.core.utils.LegekontorNotfoundException

class LegekontorService(val legekontorRepository: LegekontorRepository) {

  suspend fun getLegekontor(legekontorId: LegekontorId): Legekontor {
    return legekontorRepository.findByLegekontorId(legekontorId.value)
      ?: throw LegekontorNotfoundException()
  }
}
