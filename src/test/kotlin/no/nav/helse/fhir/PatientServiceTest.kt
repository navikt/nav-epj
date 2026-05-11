package no.nav.helse.fhir

import com.google.fhir.model.r4.Boolean
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.patient.PatientRepository

class PatientServiceTest {

  val patientRepository = mockk<PatientRepository>()

  val olaPatientId = "patient-001"
  val olaThePatient =
    Patient(
      id = olaPatientId,
      meta =
        Meta(
          profile =
            listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
            value = String(value = "12345678901"),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
            value = String(value = "01234567890"),
          ),
        ),
      active = Boolean(value = true),
      name =
        listOf(HumanName(family = String(value = "Li"), given = listOf(String(value = "Jun")))),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.fromString("1985-03-15")),
    )

  val kariPatient =
    Patient(
      id = "patient-002",
      meta =
        Meta(
          profile =
            listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
            value = String(value = "12345678902"),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
            value = String(value = "01234567891"),
          ),
        ),
      active = Boolean(value = true),
      name =
        listOf(
          HumanName(family = String(value = "Elle"), given = listOf(String(value = "McGibbons")))
        ),
      gender = Enumeration(value = AdministrativeGender.Female),
      birthDate = Date(value = FhirDate.fromString("1990-07-22")),
    )

  val perPatient =
    Patient(
      id = "patient-003",
      meta =
        Meta(
          profile =
            listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
            value = String(value = "12345678903"),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
            value = String(value = "01234567892"),
          ),
        ),
      active = Boolean(value = false),
      name =
        listOf(HumanName(family = String(value = "Wee"), given = listOf(String(value = "Jack")))),
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
    assertEquals(olaThePatient.meta, patient?.meta)
    assertEquals(olaThePatient.identifier, patient?.identifier)
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
    every { patientRepository.getAllPatients() } returns
      listOf(olaThePatient, kariPatient, perPatient)
    val patients = patientService.getAllPatients()
    verify(exactly = 1) { patientRepository.getAllPatients() }

    assertEquals(3, patients.size)
    assertTrue { patients[0].id == olaThePatient.id }
  }

  @Test
  fun `get patients returns an empty list when there are no patients`() {
    val patientService = PatientService(patientRepository)
    every { patientRepository.getAllPatients() } returns emptyList()
    val patients = patientService.getAllPatients()
    verify(exactly = 1) { patientRepository.getAllPatients() }

    assertTrue { patients.isEmpty() }
  }

  @Test
  fun `create patient successfully`() {
    val patientService = PatientService(patientRepository)
    val newPatient =
      Patient(
        id = "patient-new",
        meta =
          Meta(
            profile =
              listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
          ),
        identifier =
          listOf(
            Identifier(
              system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
              value = String(value = "12345678904"),
            ),
            Identifier(
              system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.2"),
              value = String(value = "01234567893"),
            ),
          ),
        active = Boolean(value = true),
        name =
          listOf(
            HumanName(family = String(value = "Croc"), given = listOf(String(value = "Florin")))
          ),
        gender = Enumeration(value = AdministrativeGender.Male),
        birthDate = Date(value = FhirDate.fromString("1995-05-15")),
      )
    every { patientRepository.createPatient(any()) } returns newPatient

    val created = patientService.createPatient(newPatient)
    verify(exactly = 1) { patientRepository.createPatient(newPatient) }

    assertEquals(newPatient.id, created.id)
    assertEquals(newPatient.meta, created.meta)
    assertEquals(newPatient.identifier, created.identifier)
    assertEquals(newPatient.active, created.active)
    assertEquals(newPatient.name, created.name)
    assertEquals(newPatient.gender, created.gender)
    assertEquals(newPatient.birthDate, created.birthDate)
  }
}
