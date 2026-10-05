package no.nav.helse.smart.api

import com.auth0.jwt.JWT
import com.nimbusds.oauth2.sdk.OAuth2Error
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*
import no.nav.helse.core.Environment
import no.nav.helse.core.utils.logger
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.helseId.loggedInUser
import no.nav.helse.smart.IntrospectionResponse
import no.nav.helse.smart.SmartDiscoveryDocument
import no.nav.helse.smart.TokenResponse
import no.nav.helse.smart.security.ClientAssertionVerifier
import no.nav.helse.smart.security.GrantType
import no.nav.helse.smart.security.SUPPORTED_CLIENT_ASSERTION_ALGORITHMS
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.SmartKeys
import no.nav.helse.smart.security.SmartScope
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.authenticateClient
import no.nav.helse.smart.security.codeChallengeS256
import no.nav.helse.smart.security.grantScopes
import no.nav.helse.smart.security.parseScope
import no.nav.helse.smart.security.parseScopes
import no.nav.helse.smart.security.resolveAssertedClientId
import no.nav.helse.smart.security.serialize
import no.nav.helse.smart.valkey.AuthCodeContext
import no.nav.helse.smart.valkey.ValkeyService

fun Application.configureSmartRouting() {
    val env: Environment by dependencies
    val patientService: PatientService by dependencies
    val encounterService: EncounterService by dependencies
    val valkeyService: ValkeyService by dependencies
    val clientAssertionVerifier: ClientAssertionVerifier by dependencies
    val smartKeys: SmartKeys by dependencies

    val issuerUrl = env.smart.issuerBaseUrl
    val clients = env.smart.clients
    val logger = logger()
    val launchPreparer = LaunchPreparer(valkeyService, patientService, encounterService)

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
                    logger.debug("Logged in user: {}", user)

                    val launchId =
                        when (val preparation = launchPreparer.prepare(user.hpr)) {
                            is LaunchPreparation.Ready -> preparation.launchId
                            LaunchPreparation.NoActivePatient ->
                                return@get call.respond(
                                    HttpStatusCode.Conflict,
                                    "No active patient context for clinician",
                                )
                            LaunchPreparation.PatientMismatch ->
                                return@get call.respond(HttpStatusCode.InternalServerError)
                            LaunchPreparation.UnknownPatient ->
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Unknown patient",
                                )
                            LaunchPreparation.NoActiveEncounter ->
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Found no active encounter for patient",
                                )
                            LaunchPreparation.PatientWithoutId ->
                                return@get call.respond(
                                    HttpStatusCode.InternalServerError,
                                    "FHIR Patient returned without an id",
                                )
                            LaunchPreparation.EncounterWithoutId ->
                                return@get call.respond(
                                    HttpStatusCode.InternalServerError,
                                    "FHIR Encounter returned without an id",
                                )
                        }

                    val iss = env.smart.fhirServerUrl
                    call.respondRedirect(buildLaunchUrl(appUrl, iss, launchId))
                }
            }

            appRoutes(clients, env.smart.fhirServerUrl, launchPreparer)

            route("/oidc") {
                get("/authorize") {
                    val query = call.request.queryParameters
                    // Deliberate test diagnostics
                    logger.debug("/oidc/authorize request with params {}", query.entries())

                    val redirectUri =
                        query["redirect_uri"]
                            ?: return@get rejectDirect(
                                HttpStatusCode.BadRequest,
                                "missing redirect_uri",
                            )

                    val state =
                        query["state"]
                            ?: return@get rejectDirect(HttpStatusCode.BadRequest, "missing state")

                    val scope =
                        query["scope"]
                            ?: return@get rejectMissingViaRedirect(redirectUri, state, "scope")

                    val clientId =
                        query["client_id"]
                            ?: return@get rejectDirect(
                                HttpStatusCode.BadRequest,
                                "missing client_id",
                            )

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
                        query["launch"]
                            ?: return@get rejectMissingViaRedirect(redirectUri, state, "launch")

                    // verdier som trengs for OAuth/SMART-flow
                    val responseType =
                        query["response_type"]
                            ?: return@get rejectMissingViaRedirect(
                                redirectUri,
                                state,
                                "response_type",
                            )
                    val aud =
                        query["aud"]
                            ?: return@get rejectMissingViaRedirect(redirectUri, state, "aud")
                    val codeChallenge =
                        query["code_challenge"]
                            ?: return@get rejectMissingViaRedirect(
                                redirectUri,
                                state,
                                "code_challenge",
                            )
                    val codeChallengeMethod =
                        query["code_challenge_method"]
                            ?: return@get rejectMissingViaRedirect(
                                redirectUri,
                                state,
                                "code_challenge_method",
                            )

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
                                OAuth2Error.INVALID_REQUEST.appendDescription(
                                    "Unexpected $aud is not permitted"
                                ),
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
                    val user = loggedInUser()

                    val launchContext =
                        valkeyService.getAndDeleteLaunchContext(launchId)
                            ?: return@get rejectViaRedirect(
                                redirectUri = redirectUri,
                                state = state,
                                error =
                                    OAuth2Error.INVALID_REQUEST.appendDescription(
                                        "Opaque launch token was missing, wrong or already used."
                                    ),
                            )

                    if (launchContext.hpr != user.hpr) {
                        return@get rejectViaRedirect(
                            redirectUri = redirectUri,
                            state = state,
                            error =
                                OAuth2Error.INVALID_REQUEST.appendDescription(
                                    "Launch token was not issued to the authenticated clinician."
                                ),
                        )
                    }

                    /**
                     * The scopes granted may differ from those requested (SMART app-launch:
                     * https://build.fhir.org/ig/HL7/smart-app-launch/scopes-and-launch-context.html)
                     */
                    val grantedScope = grantScopes(parseScopes(scope), acceptedClient.allowedScopes)

                    val code = UUID.randomUUID().toString()

                    valkeyService.saveAuthCode(
                        code,
                        AuthCodeContext(
                            username = user.name,
                            redirectUrl = redirectUri,
                            launch = launchContext,
                            subject = user.hpr,
                            scope = grantedScope.serialize(),
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
            // Step 4: exchange the authorisation code for an access token.
            post("/token") {
                val params = call.receiveParameters()
                // Deliberate test diagnostics
                log.debug("SMART: /token called with params: {}", params)

                if (params["grant_type"] == GrantType.CLIENT_CREDENTIALS.value) {
                    return@post issueClientCredentialsToken(
                        params,
                        clients,
                        clientAssertionVerifier,
                        issuerUrl,
                        env.smart.fhirServerUrl,
                        smartKeys,
                    )
                }

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
                val redirectUri =
                    params["redirect_uri"] ?: return@post rejectMissingToken("redirect_uri")
                val codeVerifier =
                    params["code_verifier"] ?: return@post rejectMissingToken("code_verifier")

                val assertedClientId =
                    resolveAssertedClientId(call.request, params)
                        ?: return@post rejectMissingToken("client_id")
                val acceptedClient =
                    clients.find { it.clientId == assertedClientId }
                        ?: return@post rejectToken(
                            HttpStatusCode.BadRequest,
                            OAuth2Error.INVALID_CLIENT.appendDescription("unknown client"),
                        )
                authenticateClient(call.request, acceptedClient, params, clientAssertionVerifier)
                    ?.let {
                        val challenge =
                            if (
                                acceptedClient.tokenEndpointAuthMethod ==
                                    TokenEndpointAuthMethod.CLIENT_SECRET_BASIC
                            ) {
                                "Basic"
                            } else {
                                null
                            }
                        return@post rejectToken(HttpStatusCode.Unauthorized, it, challenge)
                    }

                val ctx =
                    valkeyService.getAndDeleteAuthCode(code)
                        ?: return@post rejectToken(
                            HttpStatusCode.BadRequest,
                            OAuth2Error.INVALID_GRANT.appendDescription(
                                "unknown or already used code"
                            ),
                        )
                if (ctx.clientId != acceptedClient.clientId) {
                    return@post rejectToken(
                        HttpStatusCode.BadRequest,
                        OAuth2Error.INVALID_GRANT.appendDescription(
                            "code was not issued to this client"
                        ),
                    )
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

                log.info(
                    "SMART: issuing token for client={}, user={}, patient={}",
                    ctx.clientId,
                    ctx.username,
                    ctx.launch.patientId,
                )

                val now = Date()
                val expiresAt = Date(now.time + 3600_000)
                val grantedScopes = parseScopes(ctx.scope)
                val accessToken =
                    buildAccessToken(
                        issuerUrl,
                        env.smart.fhirServerUrl,
                        ctx.subject,
                        ctx.scope,
                        ctx.launch.patientId,
                        ctx.launch.encounterId,
                        now,
                        expiresAt,
                        smartKeys,
                    )
                val idToken =
                    if (SmartScope.Other("openid") in grantedScopes)
                        buildIdToken(issuerUrl, ctx, grantedScopes, now, expiresAt, smartKeys)
                    else null

                val hasLaunchContext = SmartScope.Other("launch") in grantedScopes
                val tokenResponse =
                    TokenResponse(
                        accessToken = accessToken,
                        idToken = idToken,
                        patient = if (hasLaunchContext) ctx.launch.patientId else null,
                        encounter = if (hasLaunchContext) ctx.launch.encounterId else null,
                        // TODO token refresh is not implemented yet.
                        refreshToken =
                            if (SmartScope.Other("offline_access") in grantedScopes)
                                UUID.randomUUID().toString()
                            else null,
                        scope = ctx.scope,
                        needPatientBanner = hasLaunchContext,
                    )
                call.respond(tokenResponse)
            }
            get("/jwks") {
                val publicJwk = smartKeys.jwk.toPublicJWK()
                call.respondText(
                    """{"keys": [${publicJwk.toJSONString()}]}""",
                    ContentType.Application.Json,
                )
            }
            // RFC 7662: lets a resource server check whether an access token it was handed is
            // still valid, without needing to understand the token format itself.
            post("/introspect") {
                val params = call.receiveParameters()
                val token = params["token"] ?: return@post rejectMissingToken("token")

                val assertedClientId =
                    resolveAssertedClientId(call.request, params)
                        ?: return@post rejectMissingToken("client_id")
                val acceptedClient =
                    clients.find { it.clientId == assertedClientId }
                        ?: return@post rejectToken(
                            HttpStatusCode.BadRequest,
                            OAuth2Error.INVALID_CLIENT.appendDescription("unknown client"),
                        )
                authenticateClient(call.request, acceptedClient, params, clientAssertionVerifier)
                    ?.let {
                        val challenge =
                            if (
                                acceptedClient.tokenEndpointAuthMethod ==
                                    TokenEndpointAuthMethod.CLIENT_SECRET_BASIC
                            ) {
                                "Basic"
                            } else {
                                null
                            }
                        return@post rejectToken(HttpStatusCode.Unauthorized, it, challenge)
                    }

                call.respond(
                    introspectAccessToken(token, issuerUrl, env.smart.fhirServerUrl, smartKeys)
                )
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
                        introspectionEndpoint = "$issuerUrl/introspect",
                        grantTypesSupported = listOf("authorization_code", "client_credentials"),
                        scopesSupported =
                            listOf(
                                "openid",
                                "fhirUser",
                                "launch",
                                "patient/*.cruds",
                                "user/*.cruds",
                                "system/Patient.rs",
                                "system/Encounter.rs",
                                "system/Condition.s",
                                "system/Observation.crs",
                                "system/Practitioner.r",
                                "system/PractitionerRole.s",
                                "system/Organization.r",
                                "system/DocumentReference.crs",
                                "offline_access",
                            ),
                        responseTypesSupported = listOf("code"),
                        codeChallengeMethodsSupported = listOf("S256"),
                        capabilities =
                            listOf(
                                "launch-ehr",
                                "permission-patient",
                                "permission-user",
                                "permission-offline",
                                "permission-system",
                                "permission-v1",
                                "permission-v2",
                                "client-public",
                                "client-confidential-symmetric",
                                "client-confidential-asymmetric",
                                "context-ehr-patient",
                                "context-ehr-encounter",
                                "context-banner",
                                "sso-openid-connect",
                            ),
                        // client_secret_post is not implemented.
                        tokenEndpointAuthMethodsSupported =
                            listOf("none", "client_secret_basic", "private_key_jwt"),
                        tokenEndpointAuthSigningAlgValuesSupported =
                            SUPPORTED_CLIENT_ASSERTION_ALGORITHMS.map { it.name },
                    ),
                )
            }
        }
    }
}

private fun buildAccessToken(
    issuerUrl: String,
    fhirServerUrl: String,
    subject: String,
    grantedScope: String,
    patientId: String?,
    encounterId: String?,
    now: Date,
    expiresAt: Date,
    smartKeys: SmartKeys,
): String =
    JWT.create()
        .withHeader(mapOf("typ" to "at+jwt"))
        .withIssuer(issuerUrl)
        .withAudience(fhirServerUrl) // RFC 9068 2.2: resource server(s) this token is valid for
        .withSubject(subject)
        .withKeyId(smartKeys.keyId)
        .withIssuedAt(now)
        .withExpiresAt(expiresAt)
        .withJWTId(
            UUID.randomUUID().toString()
        ) // RFC 9068 2.2: unique identifier for revocation/logging
        .withClaim("scope", grantedScope)
        .apply {
            patientId?.let { withClaim("patient", it) }
            encounterId?.let { withClaim("encounter", it) }
        }
        .sign(smartKeys.algorithm)

private const val CLIENT_CREDENTIALS_LIFETIME_SECONDS = 300

private suspend fun RoutingContext.authenticateClientCredentialsClient(
    params: Parameters,
    clients: List<SmartClient>,
    clientAssertionVerifier: ClientAssertionVerifier,
): SmartClient? {
    val assertedClientId = resolveAssertedClientId(call.request, params)
    if (assertedClientId == null) {
        rejectMissingToken("client_id")
        return null
    }
    val client = clients.find { it.clientId == assertedClientId }
    if (client == null) {
        rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.INVALID_CLIENT.appendDescription("unknown client"),
        )
        return null
    }
    val authenticationError =
        authenticateClient(call.request, client, params, clientAssertionVerifier)
    if (authenticationError != null) {
        rejectToken(HttpStatusCode.Unauthorized, authenticationError)
        return null
    }
    if (GrantType.CLIENT_CREDENTIALS !in client.grantTypes) {
        rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.UNAUTHORIZED_CLIENT.appendDescription(
                "client is not registered for grant_type client_credentials"
            ),
        )
        return null
    }
    return client
}

private suspend fun RoutingContext.validatedSystemScopeTokens(
    params: Parameters,
    client: SmartClient,
): List<String>? {
    val requestedScope = params["scope"]
    if (requestedScope == null) {
        rejectMissingToken("scope")
        return null
    }
    val scopeTokens = requestedScope.split(" ")
    val requested = scopeTokens.mapNotNull { parseScope(it) }.filterIsInstance<SmartScope.Fhir>()
    val invalidScopeDescription =
        when {
            scopeTokens.any { it.isEmpty() } ||
                requested.size != scopeTokens.size ||
                requested.any { it.context != ScopeContext.SYSTEM } ->
                "scope must be a space-separated list of valid system/ scopes"
            requested.any { it !in grantScopes(setOf(it), client.allowedScopes) } ->
                "scope is not pre-authorized for this client"
            else -> null
        }
    if (invalidScopeDescription != null) {
        rejectToken(
            HttpStatusCode.BadRequest,
            OAuth2Error.INVALID_SCOPE.appendDescription(invalidScopeDescription),
        )
        return null
    }
    return scopeTokens
}

private suspend fun RoutingContext.issueClientCredentialsToken(
    params: Parameters,
    clients: List<SmartClient>,
    clientAssertionVerifier: ClientAssertionVerifier,
    issuerUrl: String,
    fhirServerUrl: String,
    smartKeys: SmartKeys,
) {
    val client =
        authenticateClientCredentialsClient(params, clients, clientAssertionVerifier) ?: return
    val scopeTokens = validatedSystemScopeTokens(params, client) ?: return

    val now = Date()
    val expiresAt = Date(now.time + CLIENT_CREDENTIALS_LIFETIME_SECONDS * 1000L)
    val grantedScope = scopeTokens.distinct().joinToString(" ")
    call.application.log.info(
        "SMART: issuing client_credentials token for client={}",
        client.clientId,
    )
    call.respond(
        TokenResponse(
            accessToken =
                buildAccessToken(
                    issuerUrl,
                    fhirServerUrl,
                    client.clientId,
                    grantedScope,
                    null,
                    null,
                    now,
                    expiresAt,
                    smartKeys,
                ),
            expiresIn = CLIENT_CREDENTIALS_LIFETIME_SECONDS,
            scope = grantedScope,
            needPatientBanner = false,
        )
    )
}

private fun buildIdToken(
    issuerUrl: String,
    ctx: AuthCodeContext,
    grantedScopes: Set<SmartScope>,
    now: Date,
    expiresAt: Date,
    smartKeys: SmartKeys,
): String =
    JWT.create()
        .apply {
            if (SmartScope.Other("profile") in grantedScopes)
                withClaim("profile", "Practitioner/${ctx.subject}")
            if (SmartScope.Other("fhirUser") in grantedScopes)
                withClaim("fhirUser", "Practitioner/${ctx.subject}")
        }
        .withIssuer(issuerUrl)
        .withAudience(ctx.clientId)
        .withSubject(ctx.subject)
        .withIssuedAt(now)
        .withExpiresAt(expiresAt)
        .sign(smartKeys.algorithm)

private fun introspectAccessToken(
    token: String,
    issuerUrl: String,
    fhirServerUrl: String,
    smartKeys: SmartKeys,
): IntrospectionResponse {
    val verifier =
        JWT.require(smartKeys.algorithm).withIssuer(issuerUrl).withAudience(fhirServerUrl).build()
    val decoded = runCatching { verifier.verify(token) }.getOrNull() ?: return INACTIVE_TOKEN
    if (decoded.type != "at+jwt") return INACTIVE_TOKEN

    return IntrospectionResponse(
        active = true,
        scope = decoded.getClaim("scope").asString(),
        sub = decoded.subject,
        exp = decoded.expiresAtAsInstant?.epochSecond,
        iat = decoded.issuedAtAsInstant?.epochSecond,
        patient = decoded.getClaim("patient").asString(),
        encounter = decoded.getClaim("encounter").asString(),
    )
}

private val INACTIVE_TOKEN = IntrospectionResponse(active = false)
