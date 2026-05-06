package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Patient
import no.nav.helse.fhir.patient.repository.PatientRepository

class PatientService(private val repository: PatientRepository) {

    fun getPatient(id: String): Patient? {
        return repository.getPatient(id)
    }

    fun getAllPatients(): List<Patient> {
        return repository.getAllPatients()
    }

    fun createPatient(patient: Patient): Patient {
        return repository.createPatient(patient)
    }
}
