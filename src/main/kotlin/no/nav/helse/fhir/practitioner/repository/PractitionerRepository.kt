package no.nav.helse.fhir.practitioner.repository

import com.google.fhir.model.r4.Practitioner

interface PractitionerRepository {
  suspend fun getPractitioner(id: String): Practitioner?

  suspend fun getAllPractitioners(): List<Practitioner>

  suspend fun createPractitioner(practitioner: Practitioner): Practitioner
}
