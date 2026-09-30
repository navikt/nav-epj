package no.nav.helse.plugins

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import no.nav.helse.core.Environment
import no.nav.helse.core.SmartConfig
import no.nav.helse.smart.security.LaunchMode
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.parseRegisteredScopes
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test

private fun client(
    id: String,
    launch: List<String>,
    redirect: List<String>,
    mode: LaunchMode = LaunchMode.IFRAME,
) =
    SmartClient(
        clientId = id,
        redirectUris = redirect,
        launchUris = launch,
        tokenEndpointAuthMethod = TokenEndpointAuthMethod.NONE,
        allowedScopes = parseRegisteredScopes(listOf("openid", "launch")),
        launchMode = mode,
    )

private val clients =
    listOf(
        client(
            "syk-inn",
            launch = listOf("https://sykmelding.test/fhir/launch", "https://other.test/launch"),
            redirect = listOf("https://sykmelding.test", "https://sykmelding.test/fhir/callback"),
        ),
        client(
            "validator",
            launch = listOf("http://localhost:3000/launch"),
            redirect = listOf("http://localhost:3000"),
            mode = LaunchMode.ASK,
        ),
        client(
            "tab-only",
            launch = listOf("https://tab-only.test/launch"),
            redirect = listOf("https://tab-only.test"),
            mode = LaunchMode.TAB,
        ),
    )

class SecurityHeadersTest {

    private fun testApp(block: suspend HttpClient.() -> Unit) = testApplication {
        val environment =
            Environment(
                postgres = simpleTestEnvironment.postgres,
                smart =
                    SmartConfig(
                        issuerBaseUrl = "http://test/oidc",
                        fhirServerUrl = "http://test/fhir",
                        clients = clients,
                        privateKeyJwk = simpleTestEnvironment.smart.privateKeyJwk,
                    ),
                valkey = simpleTestEnvironment.valkey,
                epj = simpleTestEnvironment.epj,
            )
        application {
            dependencies { provide<Environment> { environment } }
            configureSecurityHeaders()
            routing { get("/ping") { call.respondText("pong") } }
        }
        client.block()
    }

    @Test
    fun `sets expected security headers`() = testApp {
        val response = get("/ping")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "frame-src 'self' http://localhost:3000 https://other.test https://sykmelding.test; " +
                "frame-ancestors 'self'",
            response.headers["Content-Security-Policy"],
        )
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals(
            "camera=(), microphone=(), geolocation=(), payment=(), usb=(), " +
                "bluetooth=(), serial=(), display-capture=()",
            response.headers["Permissions-Policy"],
        )
    }

    @Test
    fun `allows self framing and never forbids all framing`() = testApp {
        val csp = get("/ping").headers["Content-Security-Policy"].orEmpty()

        assertTrue("frame-ancestors 'self'" in csp)
        assertFalse("'none'" in csp)
        assertFalse("*" in csp)
    }

    @Test
    fun `only frames origins of registered apps that can run in a frame`() = testApp {
        val csp = get("/ping").headers["Content-Security-Policy"].orEmpty()

        assertTrue("https://sykmelding.test" in csp)
        assertTrue("http://localhost:3000" in csp)
        assertFalse("tab-only.test" in csp)
        assertFalse("/fhir/callback" in csp)
    }

    @Test
    fun `sets each header exactly once, also on unknown routes`() = testApp {
        val response = get("/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        listOf(
                "Content-Security-Policy",
                "X-Content-Type-Options",
                "Referrer-Policy",
                "Permissions-Policy",
            )
            .forEach { assertEquals(1, response.headers.getAll(it)?.size, it) }
    }

    @Test
    fun `derives distinct sorted origins from launch and redirect uris`() {
        assertEquals(
            listOf("http://localhost:3000", "https://other.test", "https://sykmelding.test"),
            frameSources(clients),
        )
    }

    @Test
    fun `keeps explicit ports and drops paths and the default port`() {
        val list =
            listOf(
                client(
                    "a",
                    launch = listOf("https://a.test:8443/x/launch"),
                    redirect = listOf("https://a.test/callback?x=1"),
                )
            )

        assertEquals(listOf("https://a.test", "https://a.test:8443"), frameSources(list))
    }

    @Test
    fun `builds a policy with only self when no app can be framed`() {
        assertEquals(
            "frame-src 'self'; frame-ancestors 'self'",
            contentSecurityPolicy(frameSources(emptyList())),
        )
    }
}
