package no.nav.helse.smart.security

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import org.junit.Test

class ClientAuthenticationTest {

  private val smartClient =
    SmartClient(
      clientId = "basic-client",
      redirectUris = emptyList(),
      launchUris = emptyList(),
      tokenEndpointAuthMethod = TokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
      clientSecret = "correct-secret",
      allowedScopes = emptySet(),
    )

  private fun basicAuthHeader(username: String, password: String) =
    "Basic " + java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray())

  @Test
  fun `valid client_secret_basic authenticates`() = testApplication {
    application {
      routing {
        get("/test") {
          val result = verifyClientSecretBasic(call.request, smartClient)
          call.respond(if (result == null) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
      }
    }
    val response =
      client.get("/test") {
        header(HttpHeaders.Authorization, basicAuthHeader("basic-client", "correct-secret"))
      }
    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun `wrong password is rejected`() = testApplication {
    application {
      routing {
        get("/test") {
          val result = verifyClientSecretBasic(call.request, smartClient)
          call.respond(if (result == null) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
      }
    }
    val response =
      client.get("/test") {
        header(HttpHeaders.Authorization, basicAuthHeader("basic-client", "wrong-secret"))
      }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `invalid base64 in the Authorization header does not crash, is rejected cleanly`() =
    testApplication {
      application {
        routing {
          get("/test") {
            val result = verifyClientSecretBasic(call.request, smartClient)
            call.respond(if (result == null) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
          }
        }
      }
      val response =
        client.get("/test") { header(HttpHeaders.Authorization, "Basic not-valid-base64!!!") }
      assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

  @Test
  fun `Authorization header without a colon is rejected cleanly`() = testApplication {
    application {
      routing {
        get("/test") {
          val result = verifyClientSecretBasic(call.request, smartClient)
          call.respond(if (result == null) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
      }
    }
    val noColon =
      "Basic " + java.util.Base64.getEncoder().encodeToString("no-colon-here".toByteArray())
    val response = client.get("/test") { header(HttpHeaders.Authorization, noColon) }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `resolveAssertedClientId reads client_id from the Basic header`() = testApplication {
    application {
      routing {
        get("/test") {
          val resolved = resolveAssertedClientId(call.request, Parameters.Empty)
          call.respondText(resolved ?: "null")
        }
      }
    }
    val response =
      client.get("/test") {
        header(HttpHeaders.Authorization, basicAuthHeader("basic-client", "correct-secret"))
      }
    assertEquals("basic-client", response.bodyAsText())
  }

  @Test
  fun `resolveAssertedClientId returns null for an invalid Authorization header`() =
    testApplication {
      application {
        routing {
          get("/test") {
            val resolved = resolveAssertedClientId(call.request, Parameters.Empty)
            call.respondText(resolved ?: "null")
          }
        }
      }
      val response =
        client.get("/test") { header(HttpHeaders.Authorization, "Basic not-valid-base64!!!") }
      assertEquals("null", response.bodyAsText())
    }
}
