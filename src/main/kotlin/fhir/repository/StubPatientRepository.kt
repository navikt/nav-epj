package no.nav.helse.fhir.repository

import com.google.fhir.model.r4.*
import com.google.fhir.model.r4.terminologies.AdministrativeGender

class StubPatientRepository : PatientRepository {

  private val patients = listOf(
    Patient(
      id = "patient-001",
      active = Boolean(value = true),
      name = listOf(
        HumanName(
          family = String(value = "Nordmann"),
          given = listOf(String(value = "Ola"))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.fromString("1985-03-15"))
    ),

    Patient(
      id = "patient-002",
      active = Boolean(value = true),
      name = listOf(
        HumanName(
          family = String(value = "Nordmann"),
          given = listOf(String(value = "Kari"))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Female),
      birthDate = Date(value = FhirDate.fromString("1990-07-22"))
    ),

    Patient(
      id = "patient-003",
      active = Boolean(value = false),
      name = listOf(
        HumanName(
          family = String(value = "Hansen"),
          given = listOf(String(value = "Per"))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.fromString("1972-11-08"))
    )
  )

  override fun getPatient(id: String): Patient? {
    return patients.find { it.id == id }
  }

  override fun getAllPatients(): List<Patient> {
    return patients
  }
}
