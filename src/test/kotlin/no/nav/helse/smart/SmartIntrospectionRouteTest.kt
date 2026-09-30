package no.nav.helse.smart

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import no.nav.helse.smart.security.SmartKeys
import no.nav.helse.utils.configureTestSmartDependencies
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class SmartIntrospectionRouteTest {

    private val smartKeys = SmartKeys(simpleTestEnvironment.smart.privateKeyJwk)

    private fun accessToken(
        expiresAt: Date = Date(System.currentTimeMillis() + 3600_000),
        type: String = "at+jwt",
    ): String =
        JWT.create()
            .withHeader(mapOf("typ" to type))
            .withIssuer(simpleTestEnvironment.smart.issuerBaseUrl)
            .withAudience(simpleTestEnvironment.smart.fhirServerUrl)
            .withSubject("111222333")
            .withKeyId(smartKeys.keyId)
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt)
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("scope", "patient/Patient.read")
            .withClaim("patient", "patient-1")
            .withClaim("encounter", "encounter-1")
            .sign(smartKeys.algorithm)

    @Test
    fun `POST oidc introspect reports a valid access token as active with its claims`() =
        testApplication {
            application { configureTestSmartDependencies() }
            val response =
                client.post("/oidc/introspect") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("token=${accessToken()}&client_id=test-client-id")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = jacksonObjectMapper().readValue<IntrospectionResponse>(response.bodyAsText())
            assertTrue(body.active)
            assertEquals("patient/Patient.read", body.scope)
            assertEquals("111222333", body.sub)
            assertEquals("patient-1", body.patient)
            assertEquals("encounter-1", body.encounter)
        }

    @Test
    fun `POST oidc introspect reports an expired access token as inactive`() = testApplication {
        application { configureTestSmartDependencies() }
        val response =
            client.post("/oidc/introspect") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    "token=${accessToken(expiresAt = Date(System.currentTimeMillis() - 1000))}" +
                        "&client_id=test-client-id"
                )
            }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = jacksonObjectMapper().readValue<IntrospectionResponse>(response.bodyAsText())
        assertFalse(body.active)
    }

    @Test
    fun `POST oidc introspect reports a token of the wrong type as inactive`() = testApplication {
        application { configureTestSmartDependencies() }
        val response =
            client.post("/oidc/introspect") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("token=${accessToken(type = "jwt")}&client_id=test-client-id")
            }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = jacksonObjectMapper().readValue<IntrospectionResponse>(response.bodyAsText())
        assertFalse(body.active)
    }

    @Test
    fun `POST oidc introspect without a token is rejected`() = testApplication {
        application { configureTestSmartDependencies() }
        val response =
            client.post("/oidc/introspect") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("client_id=test-client-id")
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST oidc introspect from an unknown client is rejected`() = testApplication {
        application { configureTestSmartDependencies() }
        val response =
            client.post("/oidc/introspect") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("token=${accessToken()}&client_id=unknown-client")
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
