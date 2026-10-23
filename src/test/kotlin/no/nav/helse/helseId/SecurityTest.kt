package no.nav.helse.helseId

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class SecurityTest {

    private fun testApp(
        config: Map<String, String>,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        environment { this.config = MapApplicationConfig(*config.toList().toTypedArray()) }
        application {
            configureHelseId()
            routing {
                authenticate("wonderwall-helseid") {
                    get("/whoami") {
                        val principal = call.principal<HelseIdPrincipal>()!!
                        call.respondText("${principal.user.hpr}:${principal.idp}")
                    }
                }
            }
        }
        block()
    }

    @Test
    fun `authenticates as the stub principal when auth localStub is true`() =
        testApp(mapOf("auth.localStub" to "true")) {
            val response = client.get("/whoami")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("565501872:${Idp.LOCAL_STUB}", response.bodyAsText())
        }

    @Test
    fun `does not install the stub when auth localStub is absent`() {
        assertFailsWith<ApplicationConfigurationException> {
            testApp(emptyMap()) { client.get("/whoami") }
        }
    }
}
