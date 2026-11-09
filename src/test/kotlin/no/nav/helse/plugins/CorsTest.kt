package no.nav.helse.plugins

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.nav.helse.core.Environment
import no.nav.helse.core.SmartConfig
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.loadSmartClients
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test

class CorsTest {
    private fun testApp(
        clients: List<SmartClient> = loadSmartClients(shippedConfig("application.yaml")),
        block: suspend HttpClient.() -> Unit,
    ) = testApplication {
        val env =
            Environment(
                postgres = simpleTestEnvironment.postgres,
                smart =
                    SmartConfig(
                        issuerBaseUrl = simpleTestEnvironment.smart.issuerBaseUrl,
                        fhirServerUrl = simpleTestEnvironment.smart.fhirServerUrl,
                        clients = clients,
                        privateKeyJwk = simpleTestEnvironment.smart.privateKeyJwk,
                    ),
                valkey = simpleTestEnvironment.valkey,
                ehr = simpleTestEnvironment.ehr,
            )
        application {
            dependencies { provide<Environment> { env } }
            configureCors()
            authentication {
                bearer("smart") {
                    authenticate { if (it.token == "test-token") UserIdPrincipal("test") else null }
                }
            }
            routing {
                authenticate("smart") {
                    post("/fhir/Observation") { call.respond(HttpStatusCode.Created) }
                }
            }
        }
        client.block()
    }

    private suspend fun HttpClient.preflight(origin: String, path: String = "/fhir/Observation") =
        options(path) {
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
        }

    @Test
    fun `shipped hackathon tab client can preflight FHIR writes and token exchange`() = testApp {
        listOf("/fhir/Observation", "/oidc/token").forEach { path ->
            val response = preflight("http://localhost:3100", path)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "http://localhost:3100",
                response.headers[HttpHeaders.AccessControlAllowOrigin],
            )
            assertTrue(response.headers[HttpHeaders.AccessControlAllowMethods]!!.contains("POST"))
            val headers = response.headers[HttpHeaders.AccessControlAllowHeaders]!!.lowercase()
            assertTrue(headers.contains("authorization"))
            assertTrue(headers.contains("content-type"))
            assertNull(response.headers[HttpHeaders.AccessControlAllowCredentials])
        }
    }

    @Test
    fun `allows both registered launch and redirect origins with their scheme and port`() {
        val registered =
            simpleTestEnvironment.smart.clients
                .single()
                .copy(
                    launchUris = listOf("https://launch.test/app/launch"),
                    redirectUris = listOf("https://callback.test:8443/app/callback"),
                )
        testApp(listOf(registered)) {
            listOf("https://launch.test", "https://callback.test:8443").forEach { origin ->
                val response = preflight(origin)

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(origin, response.headers[HttpHeaders.AccessControlAllowOrigin])
            }
            listOf(
                    "http://launch.test",
                    "https://launch.test.evil.test",
                    "https://callback.test",
                    "https://callback.test:8444",
                )
                .forEach { origin ->
                    val response = preflight(origin)

                    assertEquals(HttpStatusCode.Forbidden, response.status)
                    assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
                }
        }
    }

    @Test
    fun `rejects unregistered origins`() = testApp {
        val response = preflight("http://localhost:3101")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `keeps existing origins allowed without registered clients`() =
        testApp(emptyList()) {
            listOf(
                    "http://localhost:5173",
                    "http://localhost:5174",
                    "http://localhost:3000",
                    "http://epj.ekstern.dev.nav.no",
                )
                .forEach { origin ->
                    val response = preflight(origin)

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(origin, response.headers[HttpHeaders.AccessControlAllowOrigin])
                }
        }

    @Test
    fun `registered origins still need a bearer token for FHIR writes`() = testApp {
        val unauthenticated =
            post("/fhir/Observation") { header(HttpHeaders.Origin, "http://localhost:3100") }
        val authenticated =
            post("/fhir/Observation") {
                header(HttpHeaders.Origin, "http://localhost:3100")
                bearerAuth("test-token")
                contentType(ContentType.parse("application/fhir+json"))
                setBody("{}")
            }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.Created, authenticated.status)
        listOf(unauthenticated, authenticated).forEach { response ->
            assertEquals(
                "http://localhost:3100",
                response.headers[HttpHeaders.AccessControlAllowOrigin],
            )
            assertNull(response.headers[HttpHeaders.AccessControlAllowCredentials])
        }
    }
}
