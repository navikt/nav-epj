package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Practitioner

class PractitionerService(private val repository: PractitionerRepository) {

  suspend fun getPractitioner(id: String): Practitioner? {
    return repository.getById(id)
  }

  suspend fun getAllPractitioners(): List<Practitioner> {
    return repository.getAll()
  }

  suspend fun createPractitioner(practitioner: Practitioner): Practitioner {
    return repository.create(practitioner)
  }
}
