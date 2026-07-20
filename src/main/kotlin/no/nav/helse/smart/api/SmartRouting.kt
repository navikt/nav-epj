package no.nav.helse.smart.api

import com.auth0.jwt.JWT
import com.nimbusds.oauth2.sdk.OAuth2Error
import io.ktor.http.*
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import java.security.MessageDigest
import java.util.*
import no.nav.helse.core.Environment
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.FhirService
import no.nav.helse.helseIdAuth.loggedInUser
import no.nav.helse.smart.SmartDiscoveryDocument
import no.nav.helse.smart.TokenResponse
import no.nav.helse.smart.security.SmartKeys
import no.nav.helse.smart.security.codeChallengeS256
import no.nav.helse.smart.valkey.AuthCodeContext
import no.nav.helse.smart.valkey.LaunchContext
import no.nav.helse.smart.valkey.ValkeyService

@OptIn(ExperimentalKtorApi::class)
fun Application.configureSmartRouting() {
  val env: Environment by dependencies
  val fhirService: FhirService by dependencies
  val valkeyService: ValkeyService by dependencies

  val issuerUrl = env.smart.issuerBaseUrl
  val clients = env.smart.clients
  val logger = logger()

  routing {
    authenticate("wonderwall-helseid") {
      route("/fhir") {
        get("/launch") {
          val appUrl =
            call.request.queryParameters["url"]
              ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing app url")
          clients.find { appUrl in it.launchUris }
            ?: return@get rejectDirect(
              HttpStatusCode.BadRequest,
              "The given launch url $appUrl is not registered for any known clients",
            )
          val user = loggedInUser()

          val patientId =
            valkeyService.get(user.hpr)
              ?: return@get call.respond(
                HttpStatusCode.Conflict,
                "No active patient context for clinician",
              )

          val patient =
            fhirService.getPatient(patientId)
              ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown patient")

          val encounter = fhirService.getActiveEncounterForPatient(patientId)
          val launchId = UUID.randomUUID().toString()

          valkeyService.saveLaunchContext(launchId, LaunchContext(patient.id, encounter?.id))

          val iss = env.smart.fhirServerUrl
          call.respondRedirect("$appUrl/?iss=$iss&launch=$launchId")
        }
      }

      route("/oidc") {
        get("/authorize") {
          val query = call.request.queryParameters
          logger.info("/oidc/authorize request with params ${query.entries()}")

          val redirectUri =
            query["redirect_uri"]
              ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing redirect_uri")

          val state =
            query["state"] ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing state")

          val scope =
            query["scope"] ?: return@get rejectMissingViaRedirect(redirectUri, state, "scope")

          val clientId =
            query["client_id"]
              ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing client_id")

          val acceptedClient =
            clients.find { it.clientId == clientId }
              ?: return@get rejectDirect(
                HttpStatusCode.BadRequest,
                "Unexpected client with id $clientId is not permitted",
              )
          if (redirectUri !in acceptedClient.redirectUris) {
            return@get rejectDirect(
              HttpStatusCode.BadRequest,
              "The given redirect uri $redirectUri is not permitted for $clientId",
            )
          }

          val launchId =
            query["launch"] ?: return@get rejectMissingViaRedirect(redirectUri, state, "launch")

          // verdier som trengs for OAuth/SMART-flow
          val responseType =
            query["response_type"]
              ?: return@get rejectMissingViaRedirect(redirectUri, state, "response_type")
          val aud = query["aud"] ?: return@get rejectMissingViaRedirect(redirectUri, state, "aud")
          val codeChallenge =
            query["code_challenge"]
              ?: return@get rejectMissingViaRedirect(redirectUri, state, "code_challenge")
          val codeChallengeMethod =
            query["code_challenge_method"]
              ?: return@get rejectMissingViaRedirect(redirectUri, state, "code_challenge_method")

          if (responseType != "code") {
            return@get rejectViaRedirect(
              redirectUri = redirectUri,
              state = state,
              error =
                OAuth2Error.UNSUPPORTED_RESPONSE_TYPE.appendDescription(
                  "Unexpected response type $responseType. Must be fixed value 'code'"
                ),
            )
          }

          if (aud != env.smart.fhirServerUrl) {
            return@get rejectViaRedirect(
              redirectUri = redirectUri,
              state = state,
              error =
                OAuth2Error.INVALID_REQUEST.appendDescription("Unexpected $aud is not permitted"),
            )
          }

          if (codeChallengeMethod != "S256") {
            return@get rejectViaRedirect(
              redirectUri = redirectUri,
              state = state,
              error =
                OAuth2Error.INVALID_REQUEST.appendDescription(
                  "Unexpected code $codeChallengeMethod"
                ),
            )
          }
          val launchContext =
            valkeyService.getLaunchContext(launchId)
              ?: return@get rejectViaRedirect(
                redirectUri = redirectUri,
                state = state,
                error =
                  OAuth2Error.INVALID_REQUEST.appendDescription(
                    "Opaque launch token was missing or wrong."
                  ),
              )

          val code = UUID.randomUUID().toString()

          val user = loggedInUser()
          valkeyService.saveAuthCode(
            code,
            AuthCodeContext(
              username = user.name,
              redirectUrl = redirectUri,
              launch = launchContext,
              hpr = user.hpr,
              scope = scope,
              clientId = clientId,
              codeChallenge = codeChallenge,
            ),
          )
          call.respondRedirect("$redirectUri?code=$code&state=$state")
        }
      }
    }

    // NO AUTH
    route("/oidc") {
      // Step 4: exchange the authorization code for an access token.
      post("/token") {
        val params = call.receiveParameters()
        log.debug("SMART: /token called with params: {}", params)
        val code = params["code"] ?: return@post rejectMissingToken("code")
        val grantType = params["grant_type"] ?: return@post rejectMissingToken("grant_type")
        if (grantType != "authorization_code") {
          return@post rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.UNSUPPORTED_GRANT_TYPE.appendDescription(
              "Unexpected grant_type $grantType. Must be fixed value 'authorization_code'"
            ),
          )
        }
        val redirectUri = params["redirect_uri"] ?: return@post rejectMissingToken("redirect_uri")
        val codeVerifier =
          params["code_verifier"] ?: return@post rejectMissingToken("code_verifier")

        val ctx =
          valkeyService.getAndDeleteAuthCode(code)
            ?: return@post rejectToken(
              HttpStatusCode.BadRequest,
              OAuth2Error.INVALID_GRANT.appendDescription("unknown or already used code"),
            )
        val acceptedClient =
          clients.find { it.clientId == ctx.clientId }
            ?: return@post rejectToken(
              HttpStatusCode.BadRequest,
              OAuth2Error.INVALID_REQUEST.appendDescription("unknown client"),
            )

        if (acceptedClient.clientSecret != null) {
          val basic = call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single
          val credentials =
            basic
              ?.takeIf { it.authScheme == AuthScheme.Basic }
              ?.let { String(Base64.getDecoder().decode(it.blob)) }
          val authenticated =
            credentials != null &&
              credentials.substringBefore(":") == ctx.clientId &&
              MessageDigest.isEqual(
                credentials.substringAfter(":").toByteArray(Charsets.UTF_8),
                acceptedClient.clientSecret.toByteArray(Charsets.UTF_8),
              )
          if (!authenticated) {
            return@post rejectToken(
              HttpStatusCode.Unauthorized,
              OAuth2Error.INVALID_CLIENT.appendDescription("client authentication failed"),
            )
          }
        }

        if (codeChallengeS256(codeVerifier) != ctx.codeChallenge) {
          return@post rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.INVALID_GRANT.appendDescription(
              "code_verifier does not match code_challenge"
            ),
          )
        }

        if (redirectUri != ctx.redirectUrl) {
          return@post rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.INVALID_GRANT.appendDescription(
              "redirect_uri does not match the one used in the authorization request"
            ),
          )
        }

        log.info("SMART: issuing token for user={}, patient={}", ctx.username, ctx.launch.patientId)

        val now = Date()
        val expiresAt = Date(now.time + 3600_000)
        val grantedScope = ctx.scope
        val accessToken = buildAccessToken(issuerUrl, ctx, grantedScope, now, expiresAt)
        val idToken =
          if ("openid" in grantedScope) buildIdToken(issuerUrl, ctx, grantedScope, now, expiresAt)
          else null

        val tokenResponse =
          TokenResponse(
            accessToken = accessToken,
            idToken = idToken ?: "",
            patient = if ("launch" in grantedScope) ctx.launch.patientId.orEmpty() else "",
            encounter = if ("launch" in grantedScope) ctx.launch.encounterId.orEmpty() else "",
            // TODO token refresh is not implemented yet.
            refreshToken =
              if ("offline_access" in grantedScope) UUID.randomUUID().toString() else "",
            scope = grantedScope,
          )
        call.respond(tokenResponse)
      }
      get("/jwks") {
        call.respondText(SmartKeys.jwk.toPublicJWK().toString(), ContentType.Application.Json)
      }
    }

    route("/fhir") {
      get("/.well-known/smart-configuration") {
          call.respond(
            HttpStatusCode.OK,
            SmartDiscoveryDocument(
              issuer = issuerUrl,
              jwksUri = "$issuerUrl/jwks",
              authorizationEndpoint = "$issuerUrl/authorize",
              tokenEndpoint = "$issuerUrl/token",
              grantTypesSupported = listOf("authorization_code"),
              registrationEndpoint = "$issuerUrl/register",
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
              managementEndpoint = "$issuerUrl/user/manage",
              introspectionEndpoint = "$issuerUrl/user/introspect",
              revocationEndpoint = "$issuerUrl/user/revoke",
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
              tokenEndpointAuthMethodsSupported =
                listOf("client_secret_basic"), // TODO add private_key_jwt
            ),
          )
        }
        .describe { summary = "Get SMART on FHIR configuration" }
    }
  }
}

private fun buildAccessToken(
  issuerUrl: String,
  ctx: AuthCodeContext,
  grantedScope: String,
  now: Date,
  expiresAt: Date,
): String =
  JWT.create()
    .withIssuer(issuerUrl)
    .withSubject(ctx.hpr.toString())
    .withKeyId(SmartKeys.keyId)
    .withIssuedAt(now)
    .withExpiresAt(expiresAt)
    .withClaim("scope", grantedScope)
    .withClaim("patient", ctx.launch.patientId)
    .withClaim("encounter", ctx.launch.encounterId)
    .sign(SmartKeys.algorithm)

private fun buildIdToken(
  issuerUrl: String,
  ctx: AuthCodeContext,
  grantedScope: String,
  now: Date,
  expiresAt: Date,
): String =
  JWT.create()
    .apply {
      if ("profile" in grantedScope) withClaim("profile", "Practitioner/${ctx.hpr}")
      if ("fhirUser" in grantedScope) withClaim("fhirUser", "Practitioner/${ctx.hpr}")
    }
    .withIssuer(issuerUrl)
    .withAudience(ctx.clientId)
    .withSubject(ctx.hpr!!)
    .withIssuedAt(now)
    .withExpiresAt(expiresAt)
    .sign(SmartKeys.algorithm)
