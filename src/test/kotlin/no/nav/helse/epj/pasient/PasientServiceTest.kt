package no.nav.helse.epj.pasient

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.UgyldigPersonidentException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import org.junit.Test

class PasientServiceTest {
    private val repository = mockk<PasientRepository>()
    private val service = PasientService(repository)

    // Valid synthetic FNR for 1985-06-15 (modulus-11 checksum: 15068500017).
    private val validFnr = "15068500017"
    private val validBirthDate = LocalDate.of(1985, 6, 15)

    @Test
    fun `createPasient persists complete demographics`() = runTest {
        val inserted = slot<Pasient>()
        val request =
            OpprettPasientRequest(
                fornavn = "Kari",
                etternavn = "Nordmann",
                personident = validFnr,
                personidentType = PersonidentType.FNR,
                birthDate = validBirthDate,
                gender = AdministrativeGender.FEMALE,
            )
        coEvery {
            repository.insertOnList(capture(inserted), HelsepersonellHpr("123"), "311111116")
        } returns Unit
        coEvery { repository.findByPersonident(request.personident) } answers { inserted.captured }

        val created = service.createPasient(request, "123", "311111116")

        assertEquals(request.personident, created.personident)
        assertEquals(request.personidentType, created.personidentType)
        assertEquals(request.birthDate, created.birthDate)
        assertEquals(request.gender, created.gender)
        coVerify(exactly = 1) { repository.insertOnList(any(), any(), any()) }
        coVerify(exactly = 1) { repository.findByPersonident(request.personident) }
    }

    @Test
    fun `createPasient rejects invalid personident without inserting`() = runTest {
        val request =
            OpprettPasientRequest(
                fornavn = "Kari",
                etternavn = "Nordmann",
                personident = "00000000000",
                personidentType = PersonidentType.FNR,
                birthDate = validBirthDate,
                gender = AdministrativeGender.FEMALE,
            )

        assertFailsWith<UgyldigPersonidentException> {
            service.createPasient(request, "123", "311111116")
        }

        coVerify(exactly = 0) { repository.insertOnList(any(), any(), any()) }
        coVerify(exactly = 0) { repository.findByPersonident(any()) }
    }

    @Test
    fun `createPasient rejects birth date mismatch without inserting`() = runTest {
        val request =
            OpprettPasientRequest(
                fornavn = "Kari",
                etternavn = "Nordmann",
                personident = validFnr,
                personidentType = PersonidentType.FNR,
                birthDate = validBirthDate.plusDays(1),
                gender = AdministrativeGender.FEMALE,
            )

        assertFailsWith<UgyldigPersonidentException> {
            service.createPasient(request, "123", "311111116")
        }

        coVerify(exactly = 0) { repository.insertOnList(any(), any(), any()) }
        coVerify(exactly = 0) { repository.findByPersonident(any()) }
    }
}
