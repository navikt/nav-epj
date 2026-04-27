package no.nav.helse.fhir.practitioner.repository

import com.google.fhir.model.r4.Practitioner

interface PractitionerRepository {
  fun getPractitioner(id: String): Practitioner?
  fun getAllPractitioners(): List<Practitioner>
  fun createPractitioner(practitioner: Practitioner): Practitioner
}
