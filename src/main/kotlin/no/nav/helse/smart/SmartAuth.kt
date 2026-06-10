package no.nav.helse.smart

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
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

private data class AuthCodeContext(
  val username: String,
  val redirectUrl: String,
  val patientId: String?,
)

private val authCodes = ConcurrentHashMap<String, AuthCodeContext>()

@OptIn(ExperimentalKtorApi::class)
fun Application.configureSmartAuth() {
  val issuer = "http://localhost:8080/oidc"
  routing {
    install(CORS) { allowHost("localhost:5173") }
    route("fhir") {
      get("/launch") {
        val validatorUrl = call.request.queryParameters["url"]!!
        val patient = call.request.queryParameters["patientId"]
        val launchId =
          UUID.randomUUID()
            .toString() // TODO: øutte i en valkey  ----> Det er du som genererer den du bruker på
        // authorize endepunkt - kass launch er det i skal bruke no. primærnøkkel //
        // separer
        val iss = "http://localhost:8080/fhir"
        val redirectUrl = "$validatorUrl/" + "?iss=$iss" + "&launch=$launchId"
        call.respondRedirect(redirectUrl)
      }

      get("/.well-known/smart-configuration") {
          call.respond(
            HttpStatusCode.OK,
            SmartDiscoveryDocument(
              issuer = issuer,
              jwksUri = "$issuer/jwks",
              authorizationEndpoint = "$issuer/authorize",
              tokenEndpoint = "$issuer/token",
              grantTypesSupported = listOf("authorization_code"),
              registrationEndpoint = "$issuer/register",
              scopesSupported =
                listOf(
                  "openid",
                  "fhirUser",
                  "launch",
                  "patient/*.cruds",
                  "user/*.cruds",
                  "system/*.cruds",
                  "offline_access",
                ),
              responseTypesSupported = listOf("code"),
              managementEndpoint = "$issuer/user/manage",
              introspectionEndpoint = "$issuer/user/introspect",
              revocationEndpoint = "$issuer/user/reovke",
              codeChallengeMethodsSupported = listOf("S256"),
              capabilities =
                listOf(
                  "launch-ehr",
                  "permission-patient",
                  "permission-v2",
                  "client-public",
                  "client-confidential-symmetric",
                  "context-ehr-patient",
                  "sso-openid-connect",
                ),
              tokenEndpointAuthMethodsSupported = listOf("client_secret_basic", "private_key_jwt"),
            ),
          )
        }
        .describe { summary = "Get SMART on FHIR configuration" }
    }

    route("/oidc") {
      get("/authorize") {
        val redirectUri = call.request.queryParameters["redirect_uri"]!!
        val username = call.request.queryParameters["username"] ?: "testuser"
        val state = call.request.queryParameters["state"]!!
        val code = UUID.randomUUID().toString()

        val authCodeContext =
          AuthCodeContext(username = username, redirectUrl = redirectUri, patientId = "patientId")
        authCodes[code] = authCodeContext
        call.respondRedirect("$redirectUri?code=$code&state=$state")
      }
      post("/token") {
        val params = call.receiveParameters()
        log.debug("OIDC stub /token called with params: {}", params)
        val code =
          params["code"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing code"))
        val patientId = authCodes[code]?.patientId
        val (username, _, nonce) =
          authCodes.remove(code)
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid code"))
        log.debug("OIDC stub: issuing token for user=$username")

        val now = Date()
        val accessToken =
          JWT.create()
            .withIssuer(issuer)
            .withSubject(username)
            .withKeyId(keyId)
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + 3600_000))
            .withClaim("scope", "openid profile")
            .sign(rsaAlgorithm)

        val idToken =
          JWT.create()
            .withClaim("profile", "Practitioner/test")
            .withClaim("fhirUser", "Practitioner/test")
            .withIssuer(issuer)
            .withAudience("NAV_SMART_on_FHIR_example")
            .withSubject(username)
            .sign(rsaAlgorithm)

        val tokenResponse =
          TokenResponse(
            accessToken = accessToken,
            id_token = idToken,
            patient = patientId.toString(),
            encounter = username, // TODO: dette skal vel være noe annet
            refresh_token = UUID.randomUUID().toString(), // TODO: ikke en randomUUID
          )
        call.respond(tokenResponse)
      }
    }
  }
}
