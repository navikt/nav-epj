package no.nav.helse.core.utils

import kotlin.test.assertEquals
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class KonsultasjonStatusTest {
    private val mapper = jacksonObjectMapper()
    private val wireValues =
        mapOf(
            KonsultasjonStatus.PLANLAGT to "PLANLAGT",
            KonsultasjonStatus.PAAGAAENDE to "PÅGÅENDE",
            KonsultasjonStatus.FULLFOERT to "FULLFØRT",
            KonsultasjonStatus.AVLYST to "AVLYST",
        )

    @Test
    fun `every status keeps its original wire value`() {
        assertEquals(KonsultasjonStatus.entries.toSet(), wireValues.keys)
        wireValues.forEach { (status, value) ->
            assertEquals("\"$value\"", mapper.writeValueAsString(status))
            assertEquals(status, mapper.readValue<KonsultasjonStatus>("\"$value\""))
            assertEquals(status, KonsultasjonStatus.fromLabel(value))
            assertEquals(value, status.label)
        }
    }
}
