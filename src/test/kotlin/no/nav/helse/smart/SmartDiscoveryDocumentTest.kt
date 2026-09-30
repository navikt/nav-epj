package no.nav.helse.smart

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.nav.helse.utils.configureTestSmartDependencies
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class SmartDiscoveryDocumentTest {

    @Test
    fun `discovery document reflects accepted capabilities truthfully`() = testApplication {
        application { configureTestSmartDependencies() }
        val response = client.get("/fhir/.well-known/smart-configuration")
        assertEquals(HttpStatusCode.OK, response.status)

        val doc = jacksonObjectMapper().readValue<SmartDiscoveryDocument>(response.bodyAsText())

        assertEquals(
            listOf("none", "client_secret_basic", "private_key_jwt"),
            doc.tokenEndpointAuthMethodsSupported,
        )
        assertEquals(listOf("authorization_code"), doc.grantTypesSupported)
        assertTrue("permission-v1" in doc.capabilities)
        assertTrue("permission-v2" in doc.capabilities)
        assertTrue("permission-user" in doc.capabilities)
        assertTrue("permission-offline" in doc.capabilities)
        assertTrue("context-ehr-encounter" in doc.capabilities)
        assertEquals(listOf("S256"), doc.codeChallengeMethodsSupported)
    }

    @Test
    fun `discovery does not advertise system scopes before client_credentials exists`() =
        testApplication {
            application { configureTestSmartDependencies() }
            val response = client.get("/fhir/.well-known/smart-configuration")

            val doc = jacksonObjectMapper().readValue<SmartDiscoveryDocument>(response.bodyAsText())
            assertTrue(doc.scopesSupported.none { it.startsWith("system/") })
            assertTrue("client_credentials" !in doc.grantTypesSupported)
        }

    @Test
    fun `discovery does not advertise unimplemented registration or management endpoints`() =
        testApplication {
            application { configureTestSmartDependencies() }
            val response = client.get("/fhir/.well-known/smart-configuration")

            val body = response.bodyAsText()
            assertTrue("registration_endpoint" !in body)
            assertTrue("management_endpoint" !in body)
            assertTrue("revocation_endpoint" !in body)
        }

    @Test
    fun `discovery advertises the implemented introspection endpoint`() = testApplication {
        application { configureTestSmartDependencies() }
        val response = client.get("/fhir/.well-known/smart-configuration")

        val doc = jacksonObjectMapper().readValue<SmartDiscoveryDocument>(response.bodyAsText())
        assertEquals("${doc.issuer}/introspect", doc.introspectionEndpoint)
    }
}
