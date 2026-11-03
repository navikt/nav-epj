package no.nav.helse.epj.patient

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.InvalidPersonidentException
import no.nav.helse.epj.practitioner.PractitionerHpr
import org.junit.Test

class PatientServiceTest {
    private val repository = mockk<PatientRepository>()
    private val service = PatientService(repository)

    // Valid synthetic FNR for 1985-06-15 (modulus-11 checksum: 15068500017).
    private val validFnr = "15068500017"
    private val validBirthDate = LocalDate.of(1985, 6, 15)

    @Test
    fun `createPatient persists complete demographics`() = runTest {
        val inserted = slot<Patient>()
        val request =
            CreatePatientRequest(
                givenName = "Kari",
                familyName = "Nordmann",
                personident = validFnr,
                personidentType = PersonidentType.FNR,
                birthDate = validBirthDate,
                gender = AdministrativeGender.FEMALE,
            )
        coEvery {
            repository.insertOnList(capture(inserted), PractitionerHpr("123"), "311111116")
        } returns Unit
        coEvery { repository.findByPersonident(request.personident) } answers { inserted.captured }

        val created = service.createPatient(request, "123", "311111116")

        assertEquals(request.personident, created.personident)
        assertEquals(request.personidentType, created.personidentType)
        assertEquals(request.birthDate, created.birthDate)
        assertEquals(request.gender, created.gender)
        coVerify(exactly = 1) { repository.insertOnList(any(), any(), any()) }
        coVerify(exactly = 1) { repository.findByPersonident(request.personident) }
    }

    @Test
    fun `createPatient rejects invalid personident without inserting`() = runTest {
        val request =
            CreatePatientRequest(
                givenName = "Kari",
                familyName = "Nordmann",
                personident = "00000000000",
                personidentType = PersonidentType.FNR,
                birthDate = validBirthDate,
                gender = AdministrativeGender.FEMALE,
            )

        assertFailsWith<InvalidPersonidentException> {
            service.createPatient(request, "123", "311111116")
        }

        coVerify(exactly = 0) { repository.insertOnList(any(), any(), any()) }
        coVerify(exactly = 0) { repository.findByPersonident(any()) }
    }

    @Test
    fun `createPatient rejects birth date mismatch without inserting`() = runTest {
        val request =
            CreatePatientRequest(
                givenName = "Kari",
                familyName = "Nordmann",
                personident = validFnr,
                personidentType = PersonidentType.FNR,
                birthDate = validBirthDate.plusDays(1),
                gender = AdministrativeGender.FEMALE,
            )

        assertFailsWith<InvalidPersonidentException> {
            service.createPatient(request, "123", "311111116")
        }

        coVerify(exactly = 0) { repository.insertOnList(any(), any(), any()) }
        coVerify(exactly = 0) { repository.findByPersonident(any()) }
    }
}
