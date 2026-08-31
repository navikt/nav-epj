package no.nav.helse.epj.legekontor

import kotlin.uuid.Uuid
import no.nav.helse.core.utils.LegekontorNotfoundException

class LegekontorService(val legekontorRepository: LegekontorRepository) {

  suspend fun getLegekontor(legekontorId: LegekontorId): Legekontor {
    return legekontorRepository.findByLegekontorId(legekontorId.value)
      ?: throw LegekontorNotfoundException()
  }

  suspend fun insertIfNotExists(id: Uuid) {
    if (!legekontorRepository.legekontorInDb(id)) {
      legekontorRepository.insertLegekontor(id)
    }
  }
}
