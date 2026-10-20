package no.nav.helse.epj.legekontor

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.LegekontorNotfoundException
import no.nav.helse.utils.DEFAULT_ORGNUMMER
import no.nav.helse.utils.OTHER_ORGNUMMER
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class LegekontorServiceTest {
    private val repository = mockk<LegekontorRepository>()
    private val service = LegekontorService(repository)
    private val kontor =
        Legekontor(LegekontorId(Uuid.generateV4()), "Kontor", DEFAULT_ORGNUMMER, "1")

    @Test
    fun `returns the legekontor for its own org`() = runTest {
        coEvery { repository.findByLegekontorId(kontor.id.value) } returns kontor

        assertEquals(kontor, service.getLegekontor(kontor.id, DEFAULT_ORGNUMMER))
    }

    @Test
    fun `hides a legekontor from another org`() = runTest {
        coEvery { repository.findByLegekontorId(kontor.id.value) } returns kontor

        assertFailsWith<LegekontorNotfoundException> {
            service.getLegekontor(kontor.id, OTHER_ORGNUMMER)
        }
    }

    @Test
    fun `an unrestricted lookup returns any legekontor`() = runTest {
        coEvery { repository.findByLegekontorId(kontor.id.value) } returns kontor

        assertEquals(kontor, service.getLegekontor(kontor.id, null))
    }
}
