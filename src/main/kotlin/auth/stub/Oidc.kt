// src/main/kotlin/OidcStub.kt
package no.nav.helse.auth.stub

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.FormMethod
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.p
import kotlinx.html.passwordInput
import kotlinx.html.submitInput
import kotlinx.html.textInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val keyPair =
  KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
private val keyId = UUID.randomUUID().toString()
private val rsaPublic = keyPair.public as RSAPublicKey
private val rsaAlgorithm = Algorithm.RSA256(rsaPublic, keyPair.private as RSAPrivateKey)

private val authCodes = ConcurrentHashMap<String, Triple<String, String, String?>>()

fun Application.configureOidcStub() {
  val issuer = "http://localhost:8080/oidc"

  routing {
    route("/oidc") {

      get("/.well-known/openid-configuration") {
        call.respond(
          DiscoveryDocument(
            issuer = issuer,
            authorizationEndpoint = "$issuer/authorize",
            tokenEndpoint = "$issuer/token",
            jwksUri = "$issuer/jwks",
          ),
        )
      }

      get("/jwks") {
        val enc = Base64.getUrlEncoder().withoutPadding()
        call.respond(
          JwksResponse(
            keys = listOf(
              JwkKey(
                kty = "RSA",
                kid = keyId,
                use = "sig",
                alg = "RS256",
                n = enc.encodeToString(
                  rsaPublic.modulus.toByteArray().dropWhile { it == 0.toByte() }
                    .toByteArray(),
                ),
                e = enc.encodeToString(rsaPublic.publicExponent.toByteArray()),
              ),
            ),
          ),
        )
      }

      // Browser lands here via redirect from Ktor's oauth plugin
      get("/authorize") {
        val redirectUri = call.parameters["redirect_uri"]
          ?: return@get call.respondText(
            "Missing redirect_uri",
            status = HttpStatusCode.BadRequest,
          )
        val state = call.parameters["state"] ?: ""
        val nonce = call.parameters["nonce"]

        call.respondHtml {
          body {
            h2 { +"OIDC Stub — Logg inn" }
            form(method = FormMethod.post) {
              hiddenInput(name = "redirect_uri") { value = redirectUri }
              hiddenInput(name = "state") { value = state }
              nonce?.let { hiddenInput(name = "nonce") { value = it } }
              p {
                textInput(name = "username") {
                  value = "testuser"; placeholder = "Username"
                }
              }
              p {
                passwordInput(name = "password") {
                  value = "password"; placeholder = "Password"
                }
              }
              submitInput { value = "Sign in" }
            }
          }
        }
      }

      // Form posts here, we issue a code and redirect back
      post("/authorize") {
        val params = call.receiveParameters()
        val redirectUri = params["redirect_uri"]!!
        val state = params["state"] ?: ""
        val username = params["username"] ?: "testuser"

        log.debug("OIDC stub: issuing code for user=$username, redirecting to $redirectUri")

        val code = UUID.randomUUID().toString()
        authCodes[code] = Triple(username, redirectUri, params["nonce"])

        call.respondRedirect("$redirectUri?code=$code&state=$state")
      }

      // Ktor's oauth plugin calls this server-to-server to exchange code for tokens
      post("/token") {
        val params = call.receiveParameters()
        log.debug("OIDC stub /token called with params: {}", params)

        val code = params["code"]
          ?: return@post call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "missing code"),
          )

        val (username, _, nonce) = authCodes.remove(code)
          ?: return@post call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "invalid code"),
          )

        log.debug("OIDC stub: issuing token for user=$username")

        val now = Date()
        val accessToken = JWT.create()
          .withIssuer(issuer)
          .withSubject(username)
          .withKeyId(keyId)
          .withIssuedAt(now)
          .withExpiresAt(Date(now.time + 3600_000))
          .withClaim("scope", "openid profile")
          .sign(rsaAlgorithm)

        call.respond(TokenResponse(accessToken = accessToken))
      }
    }
  }
}

@Serializable
data class TokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("token_type") val tokenType: String = "Bearer",
  @SerialName("expires_in") val expiresIn: Int = 3600
)

@Serializable
data class DiscoveryDocument(
  val issuer: String,
  @SerialName("authorization_endpoint") val authorizationEndpoint: String,
  @SerialName("token_endpoint") val tokenEndpoint: String,
  @SerialName("jwks_uri") val jwksUri: String,
  @SerialName("response_types_supported") val responseTypesSupported: List<String> = listOf("code"),
  @SerialName("grant_types_supported") val grantTypesSupported: List<String> = listOf("authorization_code"),
  @SerialName("subject_types_supported") val subjectTypesSupported: List<String> = listOf("public"),
  @SerialName("id_token_signing_alg_values_supported") val idTokenSigningAlgValuesSupported: List<String> = listOf(
    "RS256",
  ),
)

@Serializable
data class JwksResponse(val keys: List<JwkKey>)

@Serializable
data class JwkKey(
  val kty: String,
  val kid: String,
  val use: String,
  val alg: String,
  val n: String,
  val e: String
)
