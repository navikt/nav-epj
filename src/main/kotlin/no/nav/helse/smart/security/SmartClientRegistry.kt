package no.nav.helse.smart.security

import io.ktor.server.config.*
import java.net.URI
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")

/**
 * Loads and validates the SMART client registry (step 7). Deployed environments provide the whole
 * registry as one secret-backed JSON document (`smart.clientRegistryJson`); local development may
 * keep using the readable `smart.clients` YAML list. Fails startup with a clear error for any
 * invariant violation: duplicate client IDs, missing/incompatible auth material, unsupported auth
 * methods or algorithms, insecure/wildcard redirect or launch URIs, or private key material in an
 * inline JWK Set.
 */
fun loadSmartClients(config: ApplicationConfig): List<SmartClient> {
    val raw =
        config.propertyOrNull("smart.clientRegistryJson")?.getString()?.let(::parseRegistryJson)
            ?: config.configList("smart.clients").map { it.toRawRegistration() }
    return buildRegistry(raw)
}

private fun parseRegistryJson(json: String): List<RawClientRegistration> =
    runCatching { jacksonObjectMapper().readValue<List<RawClientRegistration>>(json) }
        .getOrElse {
            throw IllegalArgumentException(
                "smart.clientRegistryJson is not a valid client registration array: ${it.message}"
            )
        }

private fun ApplicationConfig.toRawRegistration() =
    RawClientRegistration(
        clientId = property("clientId").getString(),
        redirectUris = propertyOrNull("redirectUris")?.getList() ?: emptyList(),
        launchUris = propertyOrNull("launchUris")?.getList() ?: emptyList(),
        tokenEndpointAuthMethod = propertyOrNull("tokenEndpointAuthMethod")?.getString(),
        clientSecret = propertyOrNull("clientSecret")?.getString(),
        jwksUri = propertyOrNull("jwksUri")?.getString(),
        jwkSet = propertyOrNull("jwkSet")?.getString(),
        scopes = propertyOrNull("scopes")?.getList() ?: emptyList(),
        displayName = propertyOrNull("displayName")?.getString(),
        teamSlot = propertyOrNull("teamSlot")?.getString(),
        navn = propertyOrNull("navn")?.getString(),
        beskrivelse = propertyOrNull("beskrivelse")?.getString(),
        ikon = propertyOrNull("ikon")?.getString(),
        launchMode = propertyOrNull("launchMode")?.getString(),
        grantTypes = propertyOrNull("grantTypes")?.getList(),
    )

internal fun buildRegistry(raw: List<RawClientRegistration>): List<SmartClient> {
    val duplicateIds = raw.groupingBy { it.clientId }.eachCount().filterValues { it > 1 }.keys
    require(duplicateIds.isEmpty()) {
        "smart.clients: duplicate client id(s): ${duplicateIds.joinToString()}"
    }
    return raw.map { it.toSmartClient() }
}

private fun RawClientRegistration.toSmartClient(): SmartClient {
    val method = resolveAuthMethod()
    val grants = resolveGrantTypes()
    validateAuthMaterial(method)
    redirectUris.forEach { requireSecureUri(clientId, "redirectUri", it) }
    launchUris.forEach { requireSecureUri(clientId, "launchUri", it) }
    jwksUri?.let { requireSecureUri(clientId, "jwksUri", it) }
    require(scopes.isNotEmpty()) { "smart.clients: client '$clientId' has no registered scopes" }
    val allowedScopes = parseRegisteredScopes(scopes)
    if (GrantType.CLIENT_CREDENTIALS in grants) {
        validateBackendServices(method, grants, allowedScopes)
    } else {
        require(allowedScopes.none { it is SmartScope.Fhir && it.context == ScopeContext.SYSTEM }) {
            "smart.clients: client '$clientId' registers system/ scopes without client_credentials; " +
                "system/ scopes are reserved for backend services clients"
        }
    }

    return SmartClient(
        clientId = clientId,
        redirectUris = redirectUris,
        launchUris = launchUris,
        tokenEndpointAuthMethod = method,
        clientSecret = clientSecret,
        jwksUri = jwksUri,
        inlineJwkSet = jwkSet?.let { parsePublicJwkSet(clientId, it) },
        allowedScopes = allowedScopes,
        displayName = navn ?: displayName ?: clientId,
        teamSlot = teamSlot,
        beskrivelse = beskrivelse.orEmpty(),
        ikon = ikon ?: DEFAULT_APP_ICON,
        launchMode = resolveLaunchMode(),
        grantTypes = grants,
    )
}

private fun RawClientRegistration.resolveGrantTypes(): Set<GrantType> {
    val declared = grantTypes ?: return setOf(GrantType.AUTHORIZATION_CODE)
    require(declared.isNotEmpty()) { "smart.clients: client '$clientId' declares no grantTypes" }
    return declared.mapTo(linkedSetOf()) { value ->
        runCatching { GrantType.from(value) }
            .getOrElse {
                throw IllegalArgumentException(
                    "smart.clients: client '$clientId' declares unsupported grant type " +
                        "'$value'; use one of ${GrantType.entries.joinToString { g -> g.value }}"
                )
            }
    }
}

private fun RawClientRegistration.validateBackendServices(
    method: TokenEndpointAuthMethod,
    grants: Set<GrantType>,
    allowedScopes: Set<SmartScope>,
) {
    require(grants == setOf(GrantType.CLIENT_CREDENTIALS)) {
        "smart.clients: client '$clientId' declares client_credentials together with another " +
            "grant type; a backend services client must register client_credentials only"
    }
    require(method == TokenEndpointAuthMethod.PRIVATE_KEY_JWT) {
        "smart.clients: client '$clientId' declares client_credentials but not private_key_jwt; " +
            "backend services clients must authenticate with private_key_jwt"
    }
    require(allowedScopes.all { it is SmartScope.Fhir && it.context == ScopeContext.SYSTEM }) {
        "smart.clients: client '$clientId' declares client_credentials but has non-system/ " +
            "scopes; backend services clients may only register system/ scopes"
    }
    require(redirectUris.isEmpty() && launchUris.isEmpty()) {
        "smart.clients: client '$clientId' declares client_credentials but has redirect or " +
            "launch uris"
    }
}

private fun RawClientRegistration.resolveLaunchMode(): LaunchMode =
    launchMode?.let {
        runCatching { LaunchMode.from(it) }
            .getOrElse {
                throw IllegalArgumentException(
                    "smart.clients: client '$clientId' declares unsupported launchMode " +
                        "'$launchMode'; use one of ${LaunchMode.entries.joinToString { it.value }}"
                )
            }
    } ?: LaunchMode.IFRAME

private fun RawClientRegistration.resolveAuthMethod(): TokenEndpointAuthMethod =
    tokenEndpointAuthMethod?.let {
        runCatching { TokenEndpointAuthMethod.from(it) }
            .getOrElse {
                throw IllegalArgumentException(
                    "smart.clients: client '$clientId' declares unsupported " +
                        "tokenEndpointAuthMethod '$tokenEndpointAuthMethod'; only 'none', " +
                        "'client_secret_basic' and 'private_key_jwt' authorization_code clients " +
                        "are implemented"
                )
            }
    }
        ?: if (clientSecret != null) TokenEndpointAuthMethod.CLIENT_SECRET_BASIC
        else TokenEndpointAuthMethod.NONE

private fun RawClientRegistration.validateAuthMaterial(method: TokenEndpointAuthMethod) {
    when (method) {
        TokenEndpointAuthMethod.NONE -> {
            require(clientSecret == null) {
                "smart.clients: client '$clientId' declares 'none' but has a registered clientSecret"
            }
            require(jwksUri == null && jwkSet == null) {
                "smart.clients: client '$clientId' declares 'none' but has registered jwks material"
            }
        }
        TokenEndpointAuthMethod.CLIENT_SECRET_BASIC -> {
            require(clientSecret != null) {
                "smart.clients: client '$clientId' declares client_secret_basic but has no clientSecret"
            }
            require(jwksUri == null && jwkSet == null) {
                "smart.clients: client '$clientId' declares client_secret_basic but has " +
                    "registered jwks material"
            }
        }
        TokenEndpointAuthMethod.PRIVATE_KEY_JWT -> {
            require(clientSecret == null) {
                "smart.clients: client '$clientId' declares private_key_jwt but also has a " +
                    "registered clientSecret"
            }
            require(jwksUri != null || jwkSet != null) {
                "smart.clients: client '$clientId' declares private_key_jwt but has neither a " +
                    "jwksUri nor an inline jwkSet"
            }
            require(!(jwksUri != null && jwkSet != null)) {
                "smart.clients: client '$clientId' has both a jwksUri and an inline jwkSet; " +
                    "register exactly one"
            }
        }
    }
}

private fun requireSecureUri(clientId: String, kind: String, uri: String) {
    require("*" !in uri) {
        "smart.clients: client '$clientId' has a wildcard $kind ($uri); exact URIs only, " +
            "wildcards are not permitted"
    }
    val parsed =
        runCatching { URI(uri) }
            .getOrElse {
                throw IllegalArgumentException(
                    "smart.clients: client '$clientId' has an unparseable $kind ($uri)"
                )
            }
    require(!parsed.host.isNullOrBlank()) {
        "smart.clients: client '$clientId' has a $kind without a host ($uri)"
    }
    require(parsed.scheme == "https" || (parsed.scheme == "http" && parsed.host in LOCAL_HOSTS)) {
        "smart.clients: client '$clientId' has an insecure $kind ($uri); https is required, " +
            "since plain http can be intercepted (plain http is only permitted for localhost " +
            "during local development)"
    }
}
