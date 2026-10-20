package no.nav.helse.epj.helsepersonell

import kotlin.test.assertEquals
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
    fun `a new clinician is created`() = runTest {
        val created = service.findOrCreateHelsepersonell(HelsepersonellHpr("700000001"), "Kari")

        assertEquals("Kari", created.navn)
    }

    @Test
    fun `an existing clinician is returned unchanged`() = runTest {
        val existing = service.findOrCreateHelsepersonell(HelsepersonellHpr("565501872"), "Ny")

        assertEquals("GRØNN VITS", existing.navn)
    }
}
