package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Practitioner

interface PractitionerRepository {
  suspend fun getById(id: String): Practitioner?

  suspend fun getAll(): List<Practitioner>

  suspend fun create(practitioner: Practitioner): Practitioner
}
