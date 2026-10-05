package no.nav.helse.smart

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals
import no.nav.helse.utils.configureTestSmartDependencies
import org.junit.Test

class SmartRoutingTest {

    @Test
    fun `GET fhir launch without url returns 400`() = testApplication {
        application { configureTestSmartDependencies() }
        val response = client.get("/fhir/launch")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
