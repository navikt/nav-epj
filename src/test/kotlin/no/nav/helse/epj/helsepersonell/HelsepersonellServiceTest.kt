package no.nav.helse.epj.helsepersonell

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import no.nav.helse.utils.WithPostgresql
import org.junit.Test

class HelsepersonellServiceTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    private val service = HelsepersonellService(HelsepersonellRepository())

    @Test
    fun `a new clinician is created without a legekontor`() = runTest {
        val created = service.findOrCreateHelsepersonell(HelsepersonellHpr("700000001"), "Kari")

        assertEquals("Kari", created.navn)
        assertNull(created.legekontorId)
    }

    @Test
    fun `an existing clinician keeps the stored legekontor`() = runTest {
        val existing = service.findOrCreateHelsepersonell(HelsepersonellHpr("565501872"), "Ny")

        assertEquals("GRØNN VITS", existing.navn)
        assertEquals(
            "aed5c75c-3b12-4652-83d7-223bdd69062d",
            existing.legekontorId?.value.toString(),
        )
    }
}
