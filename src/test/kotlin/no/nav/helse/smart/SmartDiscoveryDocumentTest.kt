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

    assertTrue("private_key_jwt" in doc.tokenEndpointAuthMethodsSupported)
    assertTrue("permission-v1" in doc.capabilities)
    assertTrue("permission-v2" in doc.capabilities)
    assertTrue("permission-user" in doc.capabilities)
    assertTrue("permission-offline" in doc.capabilities)
    assertTrue("context-ehr-encounter" in doc.capabilities)
    assertEquals(listOf("S256"), doc.codeChallengeMethodsSupported)
  }
}
