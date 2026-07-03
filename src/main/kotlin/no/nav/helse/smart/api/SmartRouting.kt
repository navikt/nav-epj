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
import no.nav.helse.epj.ClinicianContextStore
import no.nav.helse.fhir.FhirService
import no.nav.helse.helseIdAuth.loggedInUser
import no.nav.helse.smart.SmartDiscoveryDocument
import no.nav.helse.smart.SmartKeys
import no.nav.helse.smart.TokenResponse
import no.nav.helse.smart.codeChallengeS256
import no.nav.helse.smart.db.AuthCodeContext
import no.nav.helse.smart.db.LaunchContext
import no.nav.helse.smart.db.SingleUseStore

/**
 * Wires up the routes for the **EHR authorization server** side of a SMART App Launch (EHR launch)
 * flow, per the HL7 SMART App Launch IG. Sequence, in order:
 *
 * 1. **EHR Launch** (`GET /fhir/launch`): resolve the clinician's current patient/encounter and
 *    redirect the browser to the app's launch URL with `iss` + `launch`.
 * 2. **Discovery** (`GET /fhir/.well-known/smart-configuration`): the app discovers our endpoints.
 * 3. **Authorization code** (`GET /oidc/authorize`): the app requests a code, with a PKCE
 *    `code_challenge`.
 * 4. **Access token** (`POST /oidc/token`): the app exchanges the code plus PKCE `code_verifier`
 *    for an access token (and optionally id_token/refresh_token).
 * 5. **JWKS** (`GET /oidc/jwks`): exposes the public signing key (see [SmartKeys]).
 *
 * Step 6 ("Access FHIR API") is handled by [no.nav.helse.smart.configureSmartSecurity].
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.configureSmartRouting() {
  val env: Environment by dependencies
  val fhirService: FhirService by dependencies
  val launchStore: SingleUseStore<LaunchContext> by dependencies
  val authCodesStore: SingleUseStore<AuthCodeContext> by dependencies
  val clinicianContextStore: ClinicianContextStore by dependencies

  val issuerUrl = env.smart.issuerBaseUrl
  val clients = env.smart.clients

  routing {

    // HelseID AUTH
    authenticate("wonderwall-helseid") {
      route("/fhir") {
        // Step 1: EHR Launch. The clinician already has an EHR session (authenticated above).
        // Resolves their current patient/encounter and redirects the browser to the app's launch
        // URL with an opaque `launch` id the app echoes back at /oidc/authorize.
        get("/launch") {
          // `url`: the app's EHR-launch URL, supplied by our own frontend. Required, or we don't
          // know where to redirect.
          val appUrl =
            call.request.queryParameters["url"]
              ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing app url")

          // Enforce the registered launch-URL allow-list. Without it, `url` would let a caller
          // redirect the clinician's browser to any URL (open redirect).
          clients.find { appUrl in it.launchUris }
            ?: return@get rejectDirect(
              HttpStatusCode.BadRequest,
              "The given launch url $appUrl is not registered for any known clients",
            )
          val user = loggedInUser()

          // The clinician's currently selected patient in our own EPJ UI, resolved independently of
          // SMART/OAuth; handed to the app as EHR launch context.
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

          // Opaque, one-time-use handle (see [SingleUseStore]) the app echoes back as `launch` at
          // /oidc/authorize, so we can resolve the patient/encounter context stored here.
          val launchId = UUID.randomUUID().toString()
          launchStore.save(launchId, LaunchContext(patient.id, encounter?.id))

          // `iss` tells the app which FHIR server (and discovery document) to use.
          val iss = env.smart.fhirServerUrl
          call.respondRedirect("$appUrl/?iss=$iss&launch=$launchId")
        }
      }

      route("/oidc") {
        // Step 3: obtain an authorization code.
        //
        // Validation order matters: `client_id` and `redirect_uri` are checked first via
        // [rejectDirect] (a plain 400, never a redirect), since the server must not redirect until
        // `redirect_uri` is confirmed registered (otherwise it's an open redirector). Once it is,
        // remaining errors go back to the app via [rejectViaRedirect].
        get("/authorize") {
          val query = call.request.queryParameters
          // `client_id`: REQUIRED. Identifies the registered app; used to look up its
          // redirect_uri(s).
          val clientId =
            query["client_id"]
              ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing client_id")
          // `redirect_uri`: effectively required here, since we always validate an exact match
          // against the client's registered URIs and have nowhere safe to send the code without it.
          val redirectUri =
            query["redirect_uri"]
              ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing redirect_uri")

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
          // `state`: required in practice for SMART (CSRF protection). `redirectUri` is now
          // trusted,
          // so from here errors go back to the app via redirect, echoing `state`.
          val state =
            query["state"] ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing state")

          // `response_type`: REQUIRED, MUST be "code"; only the authorization code grant exists.
          val responseType =
            query["response_type"]
              ?: return@get rejectMissingViaRedirect(redirectUri, state, "response_type")
          // `launch`: REQUIRED for EHR launch. The opaque id from step 1; resolves the
          // patient/encounter context selected before this request.
          val launchId =
            query["launch"] ?: return@get rejectMissingViaRedirect(redirectUri, state, "launch")
          // `scope`: required for SMART (apps must request explicit scopes like `launch`,
          // `patient/*.rs`, `openid`). Stored on the code and copied onto the token as-is.
          val scope =
            query["scope"] ?: return@get rejectMissingViaRedirect(redirectUri, state, "scope")
          // `aud`: REQUIRED by SMART. The FHIR resource server URL the app wants to call; prevents
          // leaking a bearer token to a counterfeit server. In EHR launch it equals the `iss`
          // handed
          // to the app in step 1, so we check it against `env.smart.fhirServerUrl` below. We accept
          // only `aud`, not RFC 8707's `resource` synonym.
          val aud = query["aud"] ?: return@get rejectMissingViaRedirect(redirectUri, state, "aud")
          // `code_challenge`: REQUIRED (SMART mandates PKCE). `BASE64URL-ENCODE(SHA256(verifier))`,
          // computed by the app; stored verbatim and verified at /oidc/token.
          val codeChallenge =
            query["code_challenge"]
              ?: return@get rejectMissingViaRedirect(redirectUri, state, "code_challenge")
          // `code_challenge_method`: REQUIRED. Only "S256" is accepted; "plain" is rejected below.
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
          // Resolves and consumes the one-time launch context from step 1. `take` (not `get`)
          // removes it, so the same `launch` id can't be replayed against a second /authorize.
          val launch =
            launchStore.take(launchId)
              ?: return@get rejectViaRedirect(
                redirectUri = redirectUri,
                state = state,
                error =
                  OAuth2Error.INVALID_REQUEST.appendDescription(
                    "Opaque launch token was missing or wrong."
                  ),
              )

          // The authorization code: a random opaque handle into [authCodesStore], single-use and
          // consumed exactly once at /oidc/token.
          val code = UUID.randomUUID().toString()

          val user = loggedInUser()

          // The code carries no data; everything /oidc/token needs is captured on
          // [AuthCodeContext].
          authCodesStore.save(
            code,
            AuthCodeContext(
              username = user.name,
              redirectUrl = redirectUri,
              launch = LaunchContext(launch.patientId, launch.encounterId),
              hpr = user.hpr,
              scope = scope,
              clientId = clientId,
              codeChallenge = codeChallenge,
            ),
          )
          // `state` is echoed back exactly as received, since the app supplied one.
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
        // `code`: REQUIRED. The authorization code from step 3; resolved and consumed below.
        val code = params["code"] ?: return@post rejectMissingToken("code")
        // `grant_type`: REQUIRED, MUST be "authorization_code"; the only grant type implemented.
        val grantType = params["grant_type"] ?: return@post rejectMissingToken("grant_type")
        if (grantType != "authorization_code") {
          return@post rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.UNSUPPORTED_GRANT_TYPE.appendDescription(
              "Unexpected grant_type $grantType. Must be fixed value 'authorization_code'"
            ),
          )
        }
        // `redirect_uri`: REQUIRED, since it was in the /authorize request. Must be identical to
        // the one stored on the code; compared against `ctx.redirectUrl` below.
        val redirectUri = params["redirect_uri"] ?: return@post rejectMissingToken("redirect_uri")
        // `code_verifier`: REQUIRED by PKCE. Verified against the stored `code_challenge` below.
        val codeVerifier =
          params["code_verifier"] ?: return@post rejectMissingToken("code_verifier")

        // Resolves and consumes the single-use code created at /oidc/authorize.
        val ctx =
          authCodesStore.take(code)
            ?: return@post rejectToken(
              HttpStatusCode.BadRequest,
              OAuth2Error.INVALID_GRANT.appendDescription("unknown or already used code"),
            )

        // We use `ctx.clientId` (captured at /authorize), not a client-supplied `client_id`.
        // Rejecting an unknown/deregistered client here closes a gap. Without it, a client removed
        // from config after code issuance would skip the secret check below entirely.
        val acceptedClient =
          clients.find { it.clientId == ctx.clientId }
            ?: return@post rejectToken(
              HttpStatusCode.BadRequest,
              OAuth2Error.INVALID_REQUEST.appendDescription("unknown client"),
            )
        // Confidential clients (a `clientSecret` is set) MUST authenticate; public clients rely on
        // PKCE alone.
        if (acceptedClient.clientSecret != null) {
          // client_secret_basic: `client_id:secret`, base64-encoded in the Authorization header.
          val basic = call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single
          val credentials =
            basic
              ?.takeIf { it.authScheme == AuthScheme.Basic }
              ?.let { String(Base64.getDecoder().decode(it.blob)) }
          val authenticated =
            credentials != null &&
              credentials.substringBefore(":") == ctx.clientId &&
              // Constant-time compare so response timing can't leak the secret byte by byte.
              MessageDigest.isEqual(
                credentials.substringAfter(":").toByteArray(Charsets.UTF_8),
                acceptedClient.clientSecret.toByteArray(Charsets.UTF_8),
              )
          if (!authenticated) {
            // invalid_client + 401, as required when auth was attempted via the header.
            return@post rejectToken(
              HttpStatusCode.Unauthorized,
              OAuth2Error.INVALID_CLIENT.appendDescription("client authentication failed"),
            )
          }
        }

        // PKCE verification: recompute the challenge from `code_verifier` and compare to the stored
        // `code_challenge`.
        // Without this, an intercepted authorization code could be redeemed with any verifier.
        if (codeChallengeS256(codeVerifier) != ctx.codeChallenge) {
          return@post rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.INVALID_GRANT.appendDescription(
              "code_verifier does not match code_challenge"
            ),
          )
        }

        // `redirect_uri` must match the one used at /authorize.
        if (redirectUri != ctx.redirectUrl) {
          return@post rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.INVALID_GRANT.appendDescription(
              "redirect_uri does not match the one used in the authorization request"
            ),
          )
        }

        log.debug(
          "SMART: issuing token for user={}, patient={}",
          ctx.username,
          ctx.launch.patientId,
        )

        val now = Date()
        val expiresAt = Date(now.time + 3600_000)
        // No consent step exists: the clinician's HelseID session implies authorization,
        // so whatever the app requested at /authorize is granted.
        val grantedScope = ctx.scope

        val accessToken = buildAccessToken(issuerUrl, ctx, grantedScope, now, expiresAt)
        val idToken =
          if ("openid" in grantedScope) buildIdToken(issuerUrl, ctx, grantedScope, now, expiresAt)
          else null

        val tokenResponse =
          TokenResponse(
            accessToken = accessToken,
            // Empty string (not omitted) when `openid` wasn't requested; see [TokenResponse] KDoc.
            idToken = idToken ?: "",
            // SMART launch context, present only when a `launch` scope was granted.
            patient = if ("launch" in grantedScope) ctx.launch.patientId.orEmpty() else "",
            encounter = if ("launch" in grantedScope) ctx.launch.encounterId.orEmpty() else "",
            // Only issued when `offline_access` was granted.
            // TODO token refresh is not implemented yet.
            refreshToken =
              if ("offline_access" in grantedScope) UUID.randomUUID().toString() else "",
            // Always identical to the requested scope, since no consent is requested.
            scope = grantedScope,
          )
        call.respond(tokenResponse)
      }

      // Step 5: JWKS. Exposes the public signing key (see [SmartKeys]) so resource servers/apps can
      // verify token signatures, per the `jwks_uri` advertised in the discovery document.
      get("/jwks") {
        call.respondText(SmartKeys.jwk.toPublicJWK().toString(), ContentType.Application.Json)
      }
    }

    route("/fhir") {
      // Step 2: discovery. Lets the app find our endpoints via
      // `{iss}/.well-known/smart-configuration`.
      get("/.well-known/smart-configuration") {
          call.respond(
            HttpStatusCode.OK,
            SmartDiscoveryDocument(
              // `issuer`: matches the `iss` claim on tokens and the `iss` handed to the app at
              // launch.
              issuer = issuerUrl,
              // `jwks_uri`: step 5.
              jwksUri = "$issuerUrl/jwks",
              // `authorization_endpoint`: step 3.
              authorizationEndpoint = "$issuerUrl/authorize",
              // `token_endpoint`: step 4.
              tokenEndpoint = "$issuerUrl/token",
              // `grant_types_supported`: only "authorization_code" is implemented.
              grantTypesSupported = listOf("authorization_code"),
              // `registration_endpoint`: advertised only; clients are registered statically.
              registrationEndpoint = "$issuerUrl/register",
              // `scopes_supported`: SMART v2 scope syntax (`.cruds` =
              // create/read/update/delete/search)
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
              // `response_types_supported`: only "code"; no implicit grant.
              responseTypesSupported = listOf("code"),
              // `management_endpoint`: TODO advertised but not implemented.
              managementEndpoint = "$issuerUrl/user/manage",
              // `introspection_endpoint`: TODO advertised but not implemented.
              introspectionEndpoint = "$issuerUrl/user/introspect",
              // `revocation_endpoint`: TODO advertised but not implemented
              revocationEndpoint = "$issuerUrl/user/revoke",
              // `code_challenge_methods_supported`: only "S256"; "plain" is rejected at /authorize.
              codeChallengeMethodsSupported = listOf("S256"),
              // `capabilities`: SMART capability strings apps use to decide if they can work here.
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
              // `token_endpoint_auth_methods_supported`: only `client_secret_basic` is implemented.
              tokenEndpointAuthMethodsSupported =
                listOf("client_secret_basic"), // TODO add private_key_jwt
            ),
          )
        }
        .describe { summary = "Get SMART on FHIR configuration" }
    }
  }
}

/**
 * The SMART/FHIR access token: a self-contained signed JWT, so [configureSmartSecurity]'s
 * resource-server side can validate it without a network round-trip.
 */
private fun buildAccessToken(
  issuerUrl: String,
  ctx: AuthCodeContext,
  grantedScope: String,
  now: Date,
  expiresAt: Date,
): String =
  JWT.create()
    // `iss`: identifies this authorization server; matches the verifier and discovery doc.
    .withIssuer(issuerUrl)
    // `sub`: the clinician's HPR number, captured at /authorize.
    .withSubject(ctx.hpr.toString())
    // `kid` (JOSE header): lets verifiers pick the right key from /oidc/jwks.
    .withKeyId(SmartKeys.keyId)
    // `iat`: issued-at, for staleness/replay checks.
    .withIssuedAt(now)
    // `exp`: valid for 1 hour. Short-lived, since there's no revocation endpoint.
    .withExpiresAt(expiresAt)
    // `scope`: custom claim SmartSecurity reads to enforce FHIR scopes; must match the grant.
    .withClaim("scope", grantedScope)
    // `patient`/`encounter`: SMART launch context, scoping resource-server FHIR calls.
    .withClaim("patient", ctx.launch.patientId)
    .withClaim("encounter", ctx.launch.encounterId)
    .sign(SmartKeys.algorithm)

/**
 * OIDC id_token, built only when `openid` was granted. Consumed by the app itself (not the FHIR
 * server) to learn who is using it.
 */
private fun buildIdToken(
  issuerUrl: String,
  ctx: AuthCodeContext,
  grantedScope: String,
  now: Date,
  expiresAt: Date,
): String =
  JWT.create()
    .apply {
      // `profile`: only when a `profile` scope was granted. The Practitioner.
      if ("profile" in grantedScope) withClaim("profile", "Practitioner/${ctx.hpr}")
      // `fhirUser`: SMART's "which FHIR resource is the user" claim; same value as profile.
      if ("fhirUser" in grantedScope) withClaim("fhirUser", "Practitioner/${ctx.hpr}")
    }
    // `iss`: same issuer as the access token.
    .withIssuer(issuerUrl)
    // `aud`: REQUIRED to be the client_id (distinct from the SMART `aud` request param).
    .withAudience(ctx.clientId)
    // `sub`: same subject as the access token.
    .withSubject(ctx.hpr!!)
    // `iat`/`exp`: REQUIRED by OIDC; same lifetime as the access token.
    .withIssuedAt(now)
    .withExpiresAt(expiresAt)
    .sign(SmartKeys.algorithm)
