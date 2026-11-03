package no.nav.helse.fhir.patient

import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.ehr.patient.AdministrativeGender
import no.nav.helse.ehr.patient.Patient
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.patient.PatientService as EhrPatientService
import no.nav.helse.ehr.patient.PersonidentType
import org.junit.Test

class PatientServiceTest {

    private val ehrPatientService = mockk<EhrPatientService>()
    private val patientService = PatientService(ehrPatientService)

    // Synthetic FNR/DNR pair from PersonidentValidatorTest, constructed purely from the public
    // modulus-11 algorithm. Neither is an issued or real identifier.
    private val validFnr = "15068500017"
    private val validFnrBirthDate = LocalDate.of(1985, 6, 15)
    private val validDnr = "63117800026"
    private val validDnrBirthDate = LocalDate.of(1978, 11, 23)

    @OptIn(ExperimentalUuidApi::class)
    private fun patient(
        personident: String = validFnr,
        personidentType: PersonidentType? = PersonidentType.FNR,
        birthDate: LocalDate? = validFnrBirthDate,
        gender: AdministrativeGender? = AdministrativeGender.FEMALE,
    ) =
        Patient(
            id = PatientId(Uuid.generateV4()),
            givenName = "Kari",
            familyName = "Nordmann",
            personident = personident,
            personidentType = personidentType,
            birthDate = birthDate,
            gender = gender,
        )

    @Test
    fun `FNR patient maps to the fodselsnummer identifier system`() = runTest {
        val subject = patient(personident = validFnr, personidentType = PersonidentType.FNR)
        coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

        val patient = patientService.getPatient(PatientInputId(subject.id.value))

        val identifier = patient!!.identifier.single()
        assertEquals("urn:oid:2.16.578.1.12.4.1.4.1", identifier.system?.value)
        assertEquals(validFnr, identifier.value?.value)
    }

    @Test
    fun `DNR patient maps to the d-nummer identifier system`() = runTest {
        val subject = patient(personident = validDnr, personidentType = PersonidentType.DNR)
        coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

        val patient = patientService.getPatient(PatientInputId(subject.id.value))

        val identifier = patient!!.identifier.single()
        assertEquals("urn:oid:2.16.578.1.12.4.1.4.2", identifier.system?.value)
        assertEquals(validDnr, identifier.value?.value)
    }

    @Test
    fun `legacy patient with no recorded personidentType defaults to the fodselsnummer system`() =
        runTest {
            val subject = patient(personidentType = null)
            coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

            val patient = patientService.getPatient(PatientInputId(subject.id.value))

            assertEquals(
                "urn:oid:2.16.578.1.12.4.1.4.1",
                patient!!.identifier.single().system?.value,
            )
        }

    @Test
    fun `birthDate is mapped to the corresponding FHIR date`() = runTest {
        val subject = patient(birthDate = validFnrBirthDate)
        coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

        val patient = patientService.getPatient(PatientInputId(subject.id.value))

        assertEquals("1985-06-15", patient!!.birthDate?.value?.toString())
    }

    @Test
    fun `DNR patient birthDate is mapped without offsetting the day back`() = runTest {
        val subject =
            patient(
                personident = validDnr,
                personidentType = PersonidentType.DNR,
                birthDate = validDnrBirthDate,
            )
        coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

        val patient = patientService.getPatient(PatientInputId(subject.id.value))

        assertEquals("1978-11-23", patient!!.birthDate?.value?.toString())
    }

    @Test
    fun `nullable legacy demographics are omitted rather than fabricated`() = runTest {
        val subject = patient(personidentType = null, birthDate = null, gender = null)
        coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

        val patient = patientService.getPatient(PatientInputId(subject.id.value))

        assertNull(patient!!.birthDate)
        assertNull(patient.gender)
    }

    @Test
    fun `all supported administrative gender values map to their FHIR equivalent`() = runTest {
        val expected =
            mapOf(
                AdministrativeGender.MALE to "male",
                AdministrativeGender.FEMALE to "female",
                AdministrativeGender.OTHER to "other",
                AdministrativeGender.UNKNOWN to "unknown",
            )

        expected.forEach { (domainGender, fhirCode) ->
            val subject = patient(gender = domainGender)
            coEvery { ehrPatientService.getPatientById(subject.id) } returns subject

            val patient = patientService.getPatient(PatientInputId(subject.id.value))

            assertEquals(fhirCode, patient!!.gender?.value?.getCode())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `unknown patient read returns null without crashing`() = runTest {
        val id = PatientId(Uuid.generateV4())
        coEvery { ehrPatientService.getPatientById(id) } returns null

        val patient = patientService.getPatient(PatientInputId(id.value))

        assertNull(patient)
    }
}
