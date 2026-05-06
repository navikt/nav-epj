package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Encounter
import no.nav.helse.fhir.encounter.repository.EncounterRepository

class EncounterService(private val repository: EncounterRepository) {

    fun getEncounter(id: String): Encounter? {
        return repository.getEncounter(id)
    }

    fun getAllEncounters(): List<Encounter> {
        return repository.getAllEncounters()
    }

    fun createEncounter(encounter: Encounter): Encounter {
        return repository.createEncounter(encounter)
    }
}
