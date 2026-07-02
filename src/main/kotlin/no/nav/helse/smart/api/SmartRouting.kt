package no.nav.helse.smart.api

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import no.nav.helse.core.Environment
import no.nav.helse.epj.ClinicianContextStore
import no.nav.helse.fhir.FhirService
import no.nav.helse.helseIdAuth.loggedInUser
import no.nav.helse.smart.SmartDiscoveryDocument
import no.nav.helse.smart.SmartKeys
import no.nav.helse.smart.TokenResponse
import no.nav.helse.smart.db.LaunchContext
import no.nav.helse.smart.db.LaunchStore

private data class SmartAuthCodeContext(
  val username: String,
  val redirectUrl: String,
  val patientId: String?,
  val encounterId: String?,
  val hpr: String?,
  val scope: String,
  val clientId: String,
)

private val authCodes = ConcurrentHashMap<String, SmartAuthCodeContext>()

@OptIn(ExperimentalKtorApi::class)
fun Application.configureSmartRouting() {
  val env: Environment by dependencies
  val fhirService: FhirService by dependencies
  val launchStore: LaunchStore by dependencies
  val clinicianContextStore: ClinicianContextStore by dependencies

  val issuer = env.smart.issuerBaseUrl
  routing {

    // HelseID AUTH
    authenticate("wonderwall-helseid") {
      route("/fhir") {
        get("/launch") {
          val appUrl = call.request.queryParameters["url"]!!
          val user = loggedInUser()

          val context =
            clinicianContextStore.get(user.hpr)
              ?: return@get call.respond(
                HttpStatusCode.Conflict,
                "No active patient context for clinician",
              )

          val patient =
            fhirService.getPatient(context.patientId)
              ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown patient")
          val encounter = fhirService.getActiveEncounterForPatient(context.patientId)

          val launchId = UUID.randomUUID().toString()
          launchStore.save(launchId, LaunchContext(patient.id, encounter?.id))

          val iss = env.smart.fhirServerUrl

          call.respondRedirect("$appUrl/?iss=$iss&launch=$launchId")
        }
      }

      route("/oidc") {
        get("/authorize") {
          val redirectUri = call.request.queryParameters["redirect_uri"]!!
          val state = call.request.queryParameters["state"]!!
          val launchId = call.request.queryParameters["launch"]
          val launch = launchId?.let { id -> launchStore.take(id) }
          if (launch == null) {
            return@get call.respond(
              HttpStatusCode.Forbidden,
              "Opaque launch token was missing or wrong.",
            )
          }

          val code = UUID.randomUUID().toString()

          val user = loggedInUser()

          authCodes[code] =
            SmartAuthCodeContext(
              username = user.name,
              redirectUrl = redirectUri,
              patientId = launch.patientId,
              encounterId = launch.encounterId,
              hpr = user.hpr,
              scope = call.request.queryParameters["scope"] ?: "openid",
              clientId = call.request.queryParameters["clientId"] ?: "unknown",
            )
          call.respondRedirect("$redirectUri?code=$code&state=$state")
        }
      }
    }

    // NO AUTH
    route("/oidc") {
      get("/jwks") {
        call.respondText(SmartKeys.jwk.toPublicJWK().toString(), ContentType.Application.Json)
      }
      post("/token") {
        val params = call.receiveParameters()
        log.debug("SMART: /token called with params: {}", params)
        val code =
          params["code"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing code"))

        val ctx =
          authCodes.remove(code)
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid code"))
        log.debug("SMART: issuing token for user={}, patient={}", ctx.username, ctx.patientId)

        val now = Date()
        val grantedScope = ctx.scope

        val accessToken =
          JWT.create()
            .withIssuer(issuer)
            .withSubject(ctx.hpr.toString())
            .withKeyId(SmartKeys.keyId)
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + 3600_000))
            .withClaim("scope", grantedScope)
            .withClaim("patient", ctx.patientId) // optional
            .withClaim("encounter", ctx.encounterId) // optional
            .sign(SmartKeys.algorithm)

        val idToken =
          if ("openid" in grantedScope) {
            JWT.create()
              .apply {
                if ("profile" in grantedScope) {
                  withClaim("profile", "Practitioner/${ctx.hpr}")
                }
              }
              .apply {
                if ("fhirUser" in grantedScope) {
                  withClaim("fhirUser", "Practitioner/${ctx.hpr}")
                }
              }
              .withIssuer(issuer)
              .withAudience(ctx.clientId)
              .withSubject(ctx.hpr!!)
              .sign(SmartKeys.algorithm)
          } else null

        val tokenResponse =
          TokenResponse(
            accessToken = accessToken,
            idToken = idToken ?: "",
            patient = if ("launch" in grantedScope) ctx.patientId.orEmpty() else "",
            encounter = if ("launch" in grantedScope) ctx.encounterId.orEmpty() else "",
            refreshToken =
              if ("offline_access" in grantedScope) UUID.randomUUID().toString() else "",
            scope = grantedScope,
          )
        call.respond(tokenResponse)
      }
    }

    route("/fhir") {
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
  }
}
