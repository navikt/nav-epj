package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Patient

interface PatientRepository {
  fun getPatient(id: String): Patient?

  fun getAllPatients(): List<Patient>

  fun createPatient(patient: Patient): Patient
}
