package no.nav.helse.smart.security

import com.fasterxml.jackson.annotation.JsonValue
import com.nimbusds.jose.jwk.JWKSet

enum class TokenEndpointAuthMethod(@get:JsonValue val value: String) {
    NONE("none"),
    CLIENT_SECRET_BASIC("client_secret_basic"),
    PRIVATE_KEY_JWT("private_key_jwt");

    companion object {
        fun from(value: String): TokenEndpointAuthMethod =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid token endpoint: $value")
    }
}

enum class LaunchMode(@get:JsonValue val value: String) {
    IFRAME("iframe"),
    TAB("tab"),
    ASK("ask");

    companion object {
        fun from(value: String): LaunchMode =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid launchMode: $value")
    }
}

/**
 * A registered SMART authorization-code client (step 7's typed client registry). Loaded and
 * validated once at startup by [loadSmartClients] from either a secret-backed JSON registry
 * document (deployed) or readable local config (`smart.clients`), and reused as the single source
 * of truth for authorization, token issuance, and [SmartClientDisplay] metadata.
 *
 * Exactly one of [clientSecret] (`client_secret_basic`), [jwksUri], or [inlineJwkSet]
 * (`private_key_jwt`) is populated, matching [tokenEndpointAuthMethod]; `none` clients have none of
 * them.
 */
data class SmartClient(
    val clientId: String,
    val redirectUris: List<String>,
    val launchUris: List<String>,
    val tokenEndpointAuthMethod: TokenEndpointAuthMethod,
    val clientSecret: String? = null,
    val jwksUri: String? = null,
    val inlineJwkSet: JWKSet? = null,
    val allowedScopes: Set<SmartScope>,
    val displayName: String = clientId,
    val teamSlot: String? = null,
    val beskrivelse: String = "",
    val ikon: String = DEFAULT_APP_ICON,
    val launchMode: LaunchMode = LaunchMode.IFRAME,
) {
    /**
     * The safe subset of this registration a future EPJ launch picker can show a clinician: no
     * secret, no JWK material, and only launch URIs already registered (never an arbitrary URL).
     */
    fun toDisplay(): SmartClientDisplay =
        SmartClientDisplay(
            clientId = clientId,
            displayName = displayName,
            tokenEndpointAuthMethod = tokenEndpointAuthMethod,
            launchUris = launchUris,
            teamSlot = teamSlot,
        )
}

const val DEFAULT_APP_ICON = "vindu"

data class SmartClientDisplay(
    val clientId: String,
    val displayName: String,
    val tokenEndpointAuthMethod: TokenEndpointAuthMethod,
    val launchUris: List<String>,
    val teamSlot: String?,
)

data class SmartPrincipal(
    val subject: String,
    val scopes: Set<SmartScope>,
    val patient: String?,
    val encounter: String?,
)
