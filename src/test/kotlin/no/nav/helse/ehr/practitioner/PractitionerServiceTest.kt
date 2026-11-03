package no.nav.helse.ehr.practitioner

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import no.nav.helse.utils.WithPostgresql
import org.junit.Test

class PractitionerServiceTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    private val service = PractitionerService(PractitionerRepository())

    @Test
    fun `a new clinician is created`() = runTest {
        val created = service.findOrCreatePractitioner(PractitionerHpr("700000001"), "Kari")

        assertEquals("Kari", created.name)
    }

    @Test
    fun `an existing clinician is returned unchanged`() = runTest {
        service.findOrCreatePractitioner(PractitionerHpr("565501872"), "GRØNN VITS")

        val existing = service.findOrCreatePractitioner(PractitionerHpr("565501872"), "Ny")

        assertEquals("GRØNN VITS", existing.name)
    }
}
