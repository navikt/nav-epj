package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.Encounter

interface EncounterRepository {
  fun getEncounter(id: String): Encounter?
  fun getAllEncounters(): List<Encounter>
  fun createEncounter(encounter: Encounter): Encounter
}
