package no.nav.helse.smart.security

import io.ktor.server.config.*
import java.net.URI
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * The untyped shape of one client registration, shared by both supported sources: a single YAML
 * block under `smart.clients` (readable local config) and one element of the JSON array behind
 * `smart.clientRegistryJson` (a secret-backed document in deployed environments). Using the same
 * shape for both keeps this the one client model end to end; see [loadSmartClients].
 */
internal data class RawClientRegistration(
    val clientId: String,
    val redirectUris: List<String> = emptyList(),
    val launchUris: List<String> = emptyList(),
    val tokenEndpointAuthMethod: String? = null,
    val clientSecret: String? = null,
    val jwksUri: String? = null,
    val jwkSet: String? = null,
    val scopes: List<String> = emptyList(),
    val displayName: String? = null,
    val teamSlot: String? = null,
)

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
    validateAuthMaterial(method)
    redirectUris.forEach { requireSecureUri(clientId, "redirectUri", it) }
    launchUris.forEach { requireSecureUri(clientId, "launchUri", it) }
    jwksUri?.let { requireSecureUri(clientId, "jwksUri", it) }
    require(scopes.isNotEmpty()) { "smart.clients: client '$clientId' has no registered scopes" }

    return SmartClient(
        clientId = clientId,
        redirectUris = redirectUris,
        launchUris = launchUris,
        tokenEndpointAuthMethod = method,
        clientSecret = clientSecret,
        jwksUri = jwksUri,
        inlineJwkSet = jwkSet?.let { parsePublicJwkSet(clientId, it) },
        allowedScopes = parseRegisteredScopes(scopes),
        displayName = displayName ?: clientId,
        teamSlot = teamSlot,
    )
}

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
    require(parsed.scheme == "https" || (parsed.scheme == "http" && parsed.host in LOCAL_HOSTS)) {
        "smart.clients: client '$clientId' has an insecure $kind ($uri); https is required, " +
            "since plain http can be intercepted (plain http is only permitted for localhost " +
            "during local development)"
    }
}
