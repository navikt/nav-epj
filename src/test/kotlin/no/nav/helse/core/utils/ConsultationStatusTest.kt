package no.nav.helse.core.utils

import kotlin.test.assertEquals
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class ConsultationStatusTest {
    private val mapper = jacksonObjectMapper()
    private val wireValues =
        mapOf(
            ConsultationStatus.PLANNED to "PLANNED",
            ConsultationStatus.IN_PROGRESS to "IN_PROGRESS",
            ConsultationStatus.FINISHED to "FINISHED",
            ConsultationStatus.CANCELLED to "CANCELLED",
        )

    @Test
    fun `every status keeps its original wire value`() {
        assertEquals(ConsultationStatus.entries.toSet(), wireValues.keys)
        wireValues.forEach { (status, value) ->
            assertEquals("\"$value\"", mapper.writeValueAsString(status))
            assertEquals(status, mapper.readValue<ConsultationStatus>("\"$value\""))
            assertEquals(status, ConsultationStatus.fromLabel(value))
            assertEquals(value, status.label)
        }
    }
}
