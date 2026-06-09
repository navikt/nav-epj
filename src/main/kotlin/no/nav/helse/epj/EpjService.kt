package no.nav.helse.epj

import no.nav.helse.core.db.Repository

class EpjService(private val repository: Repository) {

  suspend fun getPasienter(): List<Pasient> {
    return repository.getPasienter()
  }
}
