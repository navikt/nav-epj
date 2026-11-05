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

    private fun helseIdApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            authentication { helseId(HelseIdTestTokens.verifier()) }
            routing {
                authenticate("wonderwall-helseid") {
                    get("/whoami") {
                        call.respondText(call.principal<HelseIdPrincipal>()?.user?.hpr ?: "none")
                    }
                }
            }
        }
        block()
    }

    @Test
    fun `authenticates a verified access token and reads hpr from the id token`() = helseIdApp {
        val response =
            client.get("/whoami") {
                bearerAuth(HelseIdTestTokens.accessToken())
                header("X-Wonderwall-Id-Token", HelseIdTestTokens.idToken(hpr = "565501872"))
            }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("565501872", response.bodyAsText())
    }

    @Test
    fun `responds 401 without a bearer token`() = helseIdApp {
        val response =
            client.get("/whoami") {
                header("X-Wonderwall-Id-Token", HelseIdTestTokens.idToken(hpr = "565501872"))
            }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `responds 401 when the access token fails verification`() = helseIdApp {
        val response =
            client.get("/whoami") {
                bearerAuth(HelseIdTestTokens.accessToken(iss = "https://evil.example"))
                header("X-Wonderwall-Id-Token", HelseIdTestTokens.idToken(hpr = "565501872"))
            }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
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
