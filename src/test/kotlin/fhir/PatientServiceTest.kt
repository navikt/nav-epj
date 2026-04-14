package no.nav.helse.fhir

import com.google.fhir.model.r4.Boolean
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.repository.PatientRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatientServiceTest {

  val patientRepository = mockk<PatientRepository>()

  val olaPatientId = "patient-001"
  val olaThePatient = Patient(
    id = olaPatientId,
    active = Boolean(value = true),
    name = listOf(
      HumanName(
        family = String(value = "Nordmann"),
        given = listOf(String(value = "Ola")),
      ),
    ),
    gender = Enumeration(value = AdministrativeGender.Male),
    birthDate = Date(value = FhirDate.fromString("1985-03-15")),
  )

  val kariPatient = Patient(
    id = "patient-002",
    active = Boolean(value = true),
    name = listOf(
      HumanName(
        family = String(value = "Nordmann"),
        given = listOf(String(value = "Kari")),
      ),
    ),
    gender = Enumeration(value = AdministrativeGender.Female),
    birthDate = Date(value = FhirDate.fromString("1990-07-22")),
  )

  val perPatient = Patient(
    id = "patient-003",
    active = Boolean(value = false),
    name = listOf(
      HumanName(
        family = String(value = "Hansen"),
        given = listOf(String(value = "Per")),
      ),
    ),
    gender = Enumeration(value = AdministrativeGender.Male),
    birthDate = Date(value = FhirDate.fromString("1972-11-08")),
  )

  @Test
  fun `get patient successfully and assert results`() {
    val patientService = PatientService(patientRepository)
    every { patientRepository.getPatient(any()) } returns olaThePatient
    val patient = patientService.getPatient(olaPatientId)
    verify(exactly = 1) { patientRepository.getPatient(olaPatientId) }

    assertEquals(olaThePatient.id, patient?.id)
    assertEquals(olaThePatient.active, patient?.active)
    assertEquals(olaThePatient.name, patient?.name)
    assertEquals(olaThePatient.gender, patient?.gender)
    assertEquals(olaThePatient.birthDate, patient?.birthDate)
  }

  @Test
  fun `get patient with non existing id should return null`() {
    val nonExistingId = "non-existing-id"
    val patientService = PatientService(patientRepository)
    every { patientRepository.getPatient(any()) } returns null
    val patient = patientService.getPatient(nonExistingId)
    verify(exactly = 1) { patientRepository.getPatient(nonExistingId) }

    assertEquals(null, patient)
  }

  @Test
  fun `get all patients should return all patients and assert that there are three patients`() {
    val patientService = PatientService(patientRepository)
    every { patientRepository.getAllPatients() } returns listOf(olaThePatient, kariPatient, perPatient)
    val patients = patientService.getAllPatients()
    verify (exactly = 1) { patientRepository.getAllPatients() }

    assertEquals(3, patients.size)
    assertTrue { patients[0].id == olaThePatient.id }
  }

  @Test
  fun `get patients returns an empty list when there are no patients`() {
    val patientService = PatientService(patientRepository)
    every { patientRepository.getAllPatients() } returns emptyList()
    val patients = patientService.getAllPatients()
    verify (exactly = 1) { patientRepository.getAllPatients() }

    assertTrue { patients.isEmpty() }
  }

}
