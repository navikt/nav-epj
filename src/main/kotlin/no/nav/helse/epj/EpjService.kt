package no.nav.helse.epj

import no.nav.helse.core.db.Repository

class EpjService(private val repository: Repository) {

  suspend fun getPasienter(): List<Pasient> {
    return repository.getPasienter()
  }

  suspend fun getPasient(id: String): Pasient {
    return repository.getPasient(id)
  }

  suspend fun deleteAllPasienter(): Int {
    return repository.deleteAllPasienter()
  }
}
