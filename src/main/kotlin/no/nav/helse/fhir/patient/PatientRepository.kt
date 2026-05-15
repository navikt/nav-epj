package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Patient

interface PatientRepository {
  suspend fun getById(id: String): Patient?

  suspend fun getAll(): List<Patient>

  suspend fun create(patient: Patient): Patient
}
