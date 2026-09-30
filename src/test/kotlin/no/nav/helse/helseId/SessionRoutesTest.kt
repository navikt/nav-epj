package no.nav.helse.helseId

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import no.nav.helse.plugins.configureSerialization
import org.junit.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper

private const val PID = "01019012345"
private const val HPR_CLAIM = "helseid://claims/hpr/hpr_number"

class SessionRoutesTest {

    private val mapper = jacksonObjectMapper()
    private val issuedAt = Instant.parse("2026-09-28T06:58:00Z")
    private val expiresAt = Instant.parse("2026-09-28T07:58:00Z")

    private fun idToken(audience: List<String> = listOf("nav-epj-client")): String =
        JWT.create()
            .withIssuer("https://helseid-sts.test.nhn.no")
            .withAudience(*audience.toTypedArray())
            .withClaim("name", "GRØNN VITS")
            .withClaim(HPR_CLAIM, "565501872")
            .withClaim("helseid://claims/identity/pid", PID)
            .withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256("test"))

    private fun testApp(idp: Idp, block: suspend HttpClient.() -> Unit) = testApplication {
        application {
            configureSerialization()
            authentication {
                provider("wonderwall-helseid") {
                    authenticate { ctx ->
                        ctx.principal(
                            HelseIdPrincipal(
                                User(name = "Test", hpr = "111"),
                                DebugInfo(accessToken = "access-secret", idToken = "id-secret"),
                                idp,
                            )
                        )
                    }
                }
            }
            routing { authenticate("wonderwall-helseid") { sessionRoutes() } }
        }
        client.block()
    }

    private fun json(text: String): JsonNode = mapper.readTree(text)

    @Test
    fun `returns the selected claims and the token lifetime`() =
        testApp(Idp.HELSEID) {
            val token = idToken()
            val response = get("/api/session") { header("X-Wonderwall-Id-Token", token) }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json(response.bodyAsText())
            assertEquals("helseid", body["idp"].asString())
            assertEquals(
                setOf("iss", "aud", "name", HPR_CLAIM),
                body["claims"].propertyNames().toSet(),
            )
            assertEquals("https://helseid-sts.test.nhn.no", body["claims"]["iss"].asString())
            assertEquals("nav-epj-client", body["claims"]["aud"].asString())
            assertEquals("GRØNN VITS", body["claims"]["name"].asString())
            assertEquals("565501872", body["claims"][HPR_CLAIM].asString())
            assertEquals(issuedAt, Instant.parse(body["issuedAt"].asString()))
            assertEquals(expiresAt, Instant.parse(body["expiresAt"].asString()))
        }

    @Test
    fun `never returns the pid or a raw token`() =
        testApp(Idp.HELSEID) {
            val token = idToken()
            val text =
                get("/api/session") {
                        header("X-Wonderwall-Id-Token", token)
                        header("Authorization", "Bearer access-secret")
                    }
                    .bodyAsText()

            assertFalse(text.contains(PID))
            assertFalse(text.contains("identity/pid"))
            assertFalse(text.contains(token))
            assertFalse(text.contains("access-secret"))
            assertFalse(text.contains("id-secret"))
        }

    @Test
    fun `joins multiple audiences`() =
        testApp(Idp.HELSEID) {
            val token = idToken(listOf("a", "b"))
            val body =
                json(get("/api/session") { header("X-Wonderwall-Id-Token", token) }.bodyAsText())

            assertEquals("a, b", body["claims"]["aud"].asString())
        }

    @Test
    fun `rejects a missing or malformed id token`() =
        testApp(Idp.HELSEID) {
            assertEquals(HttpStatusCode.Unauthorized, get("/api/session").status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                get("/api/session") { header("X-Wonderwall-Id-Token", "not-a-jwt") }.status,
            )
        }

    @Test
    fun `local development returns the stub session without token data`() =
        testApp(Idp.LOCAL_STUB) {
            val response = get("/api/session") { header("X-Wonderwall-Id-Token", idToken()) }

            assertEquals(HttpStatusCode.OK, response.status)
            val text = response.bodyAsText()
            val body = json(text)
            assertEquals("local-stub", body["idp"].asString())
            assertEquals(setOf("sub"), body["claims"].propertyNames().toSet())
            assertEquals("local-dev", body["claims"]["sub"].asString())
            assertEquals(setOf("idp", "claims"), body.propertyNames().toSet())
            assertFalse(text.contains(PID))
            assertFalse(text.contains("id-secret"))
        }
}
