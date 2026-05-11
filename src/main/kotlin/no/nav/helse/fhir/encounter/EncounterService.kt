package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Encounter
import no.nav.helse.fhir.encounter.repository.EncounterRepository

class EncounterService(private val repository: EncounterRepository) {

  suspend fun getEncounter(id: String): Encounter? {
    return repository.getById(id)
  }

  suspend fun getAllEncounters(): List<Encounter> {
    return repository.getAll()
  }

  suspend fun createEncounter(encounter: Encounter): Encounter {
    return repository.create(encounter)
  }
}
