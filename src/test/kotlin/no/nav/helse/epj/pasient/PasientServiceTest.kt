package no.nav.helse.epj.pasient

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PasientServiceTest {
    private val repository = mockk<PasientRepository>()
    private val service = PasientService(repository)

    @Test
    fun `createPasient persists complete demographics`() = runTest {
        val inserted = slot<Pasient>()
        val request =
            OpprettPasientRequest(
                fornavn = "Kari",
                etternavn = "Nordmann",
                personident = "12345678910",
                personidentType = PersonidentType.FNR,
                birthDate = LocalDate.of(1980, 1, 2),
                gender = AdministrativeGender.FEMALE,
            )
        coEvery { repository.insert(capture(inserted)) } returns Unit
        coEvery { repository.findByPersonident(request.personident) } answers { inserted.captured }

        val created = service.createPasient(request, "123")

        assertEquals(request.personident, created.personident)
        assertEquals(request.personidentType, created.personidentType)
        assertEquals(request.birthDate, created.birthDate)
        assertEquals(request.gender, created.gender)
        coVerify(exactly = 1) { repository.insert(any()) }
        coVerify(exactly = 1) { repository.findByPersonident(request.personident) }
    }
}
