package no.nav.helse.smart.security

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import no.nav.helse.core.Environment
import no.nav.helse.utils.TEST_PRIVATE_KEY_JWK
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test

class SmartSecurityTest {

    private val smartKeys = SmartKeys(TEST_PRIVATE_KEY_JWK)

    private fun accessToken(audience: String = simpleTestEnvironment.smart.fhirServerUrl): String =
        JWT.create()
            .withHeader(mapOf("typ" to "at+jwt"))
            .withIssuer(simpleTestEnvironment.smart.issuerBaseUrl)
            .withAudience(audience)
            .withSubject("111222333")
            .withKeyId(smartKeys.keyId)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("scope", "patient/Patient.read")
            .sign(smartKeys.algorithm)

    private fun protectedRoute(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            dependencies {
                provide<Environment> { simpleTestEnvironment }
                provide<SmartKeys> { smartKeys }
            }
            configureSmartSecurity()
            routing {
                authenticate("smart-access-token") {
                    get("/fhir/protected") { call.respondText("ok") }
                }
            }
        }
        block()
    }

    @Test
    fun `protected FHIR route accepts a correctly signed access token for the FHIR audience`() =
        protectedRoute {
            val response = client.get("/fhir/protected") { bearerAuth(accessToken()) }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `protected FHIR route rejects a correctly signed access token with another audience`() =
        protectedRoute {
            val response =
                client.get("/fhir/protected") {
                    bearerAuth(accessToken(audience = "http://other/fhir"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
