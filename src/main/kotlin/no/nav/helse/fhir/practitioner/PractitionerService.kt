package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Practitioner
import no.nav.helse.fhir.practitioner.repository.PractitionerRepository

class PractitionerService(private val repository: PractitionerRepository) {

  suspend fun getPractitioner(id: String): Practitioner? {
    return repository.getPractitioner(id)
  }

  suspend fun getAllPractitioners(): List<Practitioner> {
    return repository.getAllPractitioners()
  }

  suspend fun createPractitioner(practitioner: Practitioner): Practitioner {
    return repository.createPractitioner(practitioner)
  }
}
