package no.nav.helse.fhir.patient.repository

import com.google.fhir.model.r4.*
import com.google.fhir.model.r4.terminologies.AdministrativeGender

class StubPatientRepository : PatientRepository {

  private val patients = mutableListOf(
      Patient(
          id = "patient-001",
          meta = Meta(
              profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
          ),
          identifier = listOf(
              Identifier(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
                  value = String(value = "12345678901")
              ),
              Identifier(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
                  value = String(value = "01234567890")
              )
          ),
          active = Boolean(value = true),
          name = listOf(
              HumanName(
                  family = String(value = "Li"),
                  given = listOf(String(value = "Jun"))
              )
          ),
          gender = Enumeration(value = AdministrativeGender.Male),
          birthDate = Date(value = FhirDate.Companion.fromString("1985-03-15"))
      ),

      Patient(
          id = "patient-002",
          meta = Meta(
              profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
          ),
          identifier = listOf(
              Identifier(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
                  value = String(value = "12345678902")
              ),
              Identifier(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
                  value = String(value = "01234567891")
              )
          ),
          active = Boolean(value = true),
          name = listOf(
              HumanName(
                  family = String(value = "McGibbons"),
                  given = listOf(String(value = "Elle"))
              )
          ),
          gender = Enumeration(value = AdministrativeGender.Female),
          birthDate = Date(value = FhirDate.Companion.fromString("1990-07-22"))
      ),

      Patient(
          id = "patient-003",
          meta = Meta(
              profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
          ),
          identifier = listOf(
              Identifier(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
                  value = String(value = "12345678903")
              ),
              Identifier(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
                  value = String(value = "01234567892")
              )
          ),
          active = Boolean(value = false),
          name = listOf(
              HumanName(
                  family = String(value = "Wee"),
                  given = listOf(String(value = "Jack"))
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

  override fun createPatient(patient: Patient): Patient {
    val newPatient = if (patient.id == null) {
      patient.copy(id = "patient-${java.util.UUID.randomUUID()}")
    } else {
      patient
    }
    patients.add(newPatient)
    return newPatient
  }
}
