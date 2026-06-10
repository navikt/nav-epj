package no.nav.helse.epj

import no.nav.helse.epj.api.pasient.Pasient
import no.nav.helse.epj.db.PasientRepository

class EpjService(private val repository: PasientRepository) {

  suspend fun getPasienter(): List<Pasient> {
    return repository.getAllPasients()
  }

  suspend fun getPasient(id: String): Pasient? {
    return repository.getPasient(id)
  }

  suspend fun deleteAllPasienter(): Int {
    return repository.deleteAllPasients()
  }
}
