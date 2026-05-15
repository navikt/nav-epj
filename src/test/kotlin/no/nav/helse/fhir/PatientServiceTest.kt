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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.patient.PatientRepository
import no.nav.helse.fhir.patient.PatientService

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
  fun `should return patient when id exists`() = runBlocking {
    val patientService = PatientService(patientRepository)
    coEvery { patientRepository.getById(any()) } returns olaThePatient

    val patient = patientService.getPatient(olaPatientId)

    coVerify(exactly = 1) { patientRepository.getById(olaPatientId) }
    assertEquals(olaThePatient.id, patient?.id)
    assertEquals(olaThePatient.meta, patient?.meta)
    assertEquals(olaThePatient.identifier, patient?.identifier)
    assertEquals(olaThePatient.active, patient?.active)
    assertEquals(olaThePatient.name, patient?.name)
    assertEquals(olaThePatient.gender, patient?.gender)
    assertEquals(olaThePatient.birthDate, patient?.birthDate)
  }

  @Test
  fun `should return null when patient id does not exist`() = runBlocking {
    val nonExistingId = "non-existing-id"
    val patientService = PatientService(patientRepository)
    coEvery { patientRepository.getById(any()) } returns null

    val patient = patientService.getPatient(nonExistingId)

    coVerify(exactly = 1) { patientRepository.getById(nonExistingId) }
    assertEquals(null, patient)
  }

  @Test
  fun `should return all patients`() = runBlocking {
    val patientService = PatientService(patientRepository)
    coEvery { patientRepository.getAll() } returns listOf(olaThePatient, kariPatient, perPatient)

    val patients = patientService.getAllPatients()

    coVerify(exactly = 1) { patientRepository.getAll() }
    assertEquals(3, patients.size)
    assertTrue { patients[0].id == olaThePatient.id }
  }

  @Test
  fun `should return empty list when no patients exist`() = runBlocking {
    val patientService = PatientService(patientRepository)
    coEvery { patientRepository.getAll() } returns emptyList()

    val patients = patientService.getAllPatients()

    coVerify(exactly = 1) { patientRepository.getAll() }
    assertTrue { patients.isEmpty() }
  }

  @Test
  fun `should create patient successfully`() = runBlocking {
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
    coEvery { patientRepository.create(any()) } returns newPatient

    val created = patientService.createPatient(newPatient)

    coVerify(exactly = 1) { patientRepository.create(newPatient) }
    assertEquals(newPatient.id, created.id)
    assertEquals(newPatient.meta, created.meta)
    assertEquals(newPatient.identifier, created.identifier)
    assertEquals(newPatient.active, created.active)
    assertEquals(newPatient.name, created.name)
    assertEquals(newPatient.gender, created.gender)
    assertEquals(newPatient.birthDate, created.birthDate)
  }
}
