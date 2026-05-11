package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.Encounter

interface EncounterRepository {
  suspend fun getById(id: String): Encounter?

  suspend fun getAll(): List<Encounter>

  suspend fun create(encounter: Encounter): Encounter
}
