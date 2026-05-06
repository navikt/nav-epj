package no.nav.helse

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.nav.helse.auth.UserSession

class ApplicationTest {

    private fun testConfig() =
        io.ktor.server.config.MapApplicationConfig(
            "ktor.environment" to "local",
            "oauth.authorizeUrl" to "http://localhost/oidc/authorize",
            "oauth.accessTokenUrl" to "http://localhost/oidc/token",
            "oauth.clientId" to "dr-zara-web",
            "oauth.clientSecret" to "secret",
            "oauth.callbackUrl" to "http://localhost/callback",
            "oauth.defaultScopes" to "openid,profile",
        )

    //  @Test
    //  fun testRoot() = testApplication {
    //    environment {
    //      config = testConfig()
    //    }
    //    application {
    //      module()
    //    }
    //    client.get("/").apply {
    //      assertEquals(HttpStatusCode.OK, status)
    //    }
    //  }

    @Test
    fun `accessing home without login redirects to login`() = testApplication {
        environment { config = testConfig() }
        application { module() }
        val client = createClient { followRedirects = false }

        client.get("/home").apply {
            status shouldBe HttpStatusCode.Found
            headers[HttpHeaders.Location] shouldContain "home"
        }
    }

    @Test
    @Ignore("Fix proper oauth flow with expected token and state")
    fun `logged in user can access home endpoint`() = testApplication {
        environment { config = testConfig() }
        application { module() }
        val client = createClient { install(HttpCookies) }

        // Generate a test JWT token
        val keyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val algorithm =
            Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
        val token =
            JWT.create()
                .withSubject("testuser")
                .withIssuer("http://localhost/oidc")
                .withIssuedAt(Date())
                .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
                .sign(algorithm)

        // Set up session by simulating a logged-in user via session cookie
        val helloResponse =
            client.get("/home") {
                cookie(
                    "user_session",
                    UserSession("", token).let {
                        // Serialize the session - Ktor uses simple serialization for data classes
                        """{"accessToken":"$token"}"""
                    },
                )
            }

        // helloResponse.status shouldBe HttpStatusCode.OK
        assertEquals(HttpStatusCode.OK, helloResponse.status)
        assertTrue(helloResponse.bodyAsText().contains("Hello, testuser!"))
    }
}
