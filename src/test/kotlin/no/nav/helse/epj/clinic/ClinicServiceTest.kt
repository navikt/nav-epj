package no.nav.helse.epj.clinic

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ClinicNotFoundException
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class ClinicServiceTest {
    private val repository = mockk<ClinicRepository>()
    private val service = ClinicService(repository)
    private val clinic = Clinic(ClinicId(Uuid.generateV4()), "Kontor", DEFAULT_ORG_NUMBER, "1")

    @Test
    fun `returns the clinic for its own org`() = runTest {
        coEvery { repository.findByClinicId(clinic.id.value) } returns clinic

        assertEquals(clinic, service.getClinic(clinic.id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `hides a clinic from another org`() = runTest {
        coEvery { repository.findByClinicId(clinic.id.value) } returns clinic

        assertFailsWith<ClinicNotFoundException> { service.getClinic(clinic.id, OTHER_ORG_NUMBER) }
    }

    @Test
    fun `an unrestricted lookup returns any clinic`() = runTest {
        coEvery { repository.findByClinicId(clinic.id.value) } returns clinic

        assertEquals(clinic, service.getClinic(clinic.id, null))
    }
}
