package no.nav.helse

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

  private fun testConfig() = io.ktor.server.config.MapApplicationConfig(
    "ktor.environment" to "local",
    "oauth.authorizeUrl" to "http://localhost/oidc/authorize",
    "oauth.accessTokenUrl" to "http://localhost/oidc/token",
    "oauth.clientId" to "dr-zara-web",
    "oauth.clientSecret" to "secret",
    "oauth.callbackUrl" to "http://localhost/callback",
    "oauth.defaultScopes" to "openid,profile"
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
  fun `accessing hello without login redirects to login`() = testApplication {
    environment {
      config = testConfig()
    }
    application {
      module()
    }
    val client = createClient {
      followRedirects = false
    }

    client.get("/hello").apply {
      assertEquals(HttpStatusCode.Found, status)
      assertEquals("/login", headers[HttpHeaders.Location])
    }
  }

  @Test
  fun `logged in user can access hello endpoint`() = testApplication {
    environment {
      config = testConfig()
    }
    application {
      module()
    }
    val client = createClient {
      install(HttpCookies)
    }

    // Generate a test JWT token
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val algorithm = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
    val token = JWT.create()
      .withSubject("testuser")
      .withIssuer("http://localhost/oidc")
      .withIssuedAt(Date())
      .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
      .sign(algorithm)

    // Set up session by simulating a logged-in user via session cookie
    val helloResponse = client.get("/hello") {
      cookie("USER_SESSION", UserSession(token).let {
        // Serialize the session - Ktor uses simple serialization for data classes
        """{"accessToken":"$token"}"""
      })
    }

    assertEquals(HttpStatusCode.OK, helloResponse.status)
    assertTrue(helloResponse.bodyAsText().contains("Hello, testuser!"))
  }

}
