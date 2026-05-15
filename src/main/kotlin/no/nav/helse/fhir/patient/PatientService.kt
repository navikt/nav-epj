package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Patient

class PatientService(private val repository: PatientRepository) {

  suspend fun getPatient(id: String): Patient? {
    return repository.getById(id)
  }

  suspend fun getAllPatients(): List<Patient> {
    return repository.getAll()
  }

  suspend fun createPatient(patient: Patient): Patient {
    return repository.create(patient)
  }
}
