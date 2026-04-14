package no.nav.helse.fhir.patient.repository

import com.google.fhir.model.r4.Boolean
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.terminologies.AdministrativeGender

class StubPatientRepository : PatientRepository {

  private val patients = listOf(
      Patient(
          id = "patient-001",
          active = Boolean(value = true),
          name = listOf(
              HumanName(
                  family = com.google.fhir.model.r4.String(value = "Nordmann"),
                  given = listOf(com.google.fhir.model.r4.String(value = "Ola"))
              )
          ),
          gender = Enumeration(value = AdministrativeGender.Male),
          birthDate = Date(value = FhirDate.Companion.fromString("1985-03-15"))
      ),

      Patient(
          id = "patient-002",
          active = Boolean(value = true),
          name = listOf(
              HumanName(
                  family = com.google.fhir.model.r4.String(value = "Nordmann"),
                  given = listOf(com.google.fhir.model.r4.String(value = "Kari"))
              )
          ),
          gender = Enumeration(value = AdministrativeGender.Female),
          birthDate = Date(value = FhirDate.Companion.fromString("1990-07-22"))
      ),

      Patient(
          id = "patient-003",
          active = Boolean(value = false),
          name = listOf(
              HumanName(
                  family = com.google.fhir.model.r4.String(value = "Hansen"),
                  given = listOf(com.google.fhir.model.r4.String(value = "Per"))
              )
          ),
          gender = Enumeration(value = AdministrativeGender.Male),
          birthDate = Date(value = FhirDate.Companion.fromString("1972-11-08"))
      )
  )

  override fun getPatient(id: String): Patient? {
    return patients.find { it.id == id }
  }

  override fun getAllPatients(): List<Patient> {
    return patients
  }
}
