package no.nav.helse.smart.security

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
    val navn: String? = null,
    val beskrivelse: String? = null,
    val ikon: String? = null,
    val launchMode: String? = null,
    val grantTypes: List<String>? = null,
) {
    override fun toString(): String =
        "RawClientRegistration(clientId=$clientId, teamSlot=$teamSlot, credentials=redacted)"
}
