package no.nav.helse.hackathon.credentials

import java.nio.file.Path
import no.nav.helse.smart.security.RawClientRegistration
import no.nav.helse.smart.security.buildRegistry
import tools.jackson.databind.JsonNode

private const val MAX_EXISTING_REGISTRATIONS = 500
private const val MAX_JWK_SET = 65_536
private const val BACKEND_GRANT = "client_credentials"
private val INTERACTIVE_METHODS = setOf("none", "client_secret_basic", "private_key_jwt")
private val STRING_FIELDS =
    setOf(
        "clientId",
        "tokenEndpointAuthMethod",
        "clientSecret",
        "jwksUri",
        "jwkSet",
        "displayName",
        "teamSlot",
        "navn",
        "beskrivelse",
        "ikon",
        "launchMode",
    )
private val LIST_FIELDS = setOf("redirectUris", "launchUris", "scopes", "grantTypes")

internal fun readExistingRegistry(path: Path): List<RawClientRegistration> {
    val root = readJsonInput("existing registry", path)
    ensure(root.isArray && root.size() in 1..MAX_EXISTING_REGISTRATIONS) {
        "existing registry must be a JSON array of 1..$MAX_EXISTING_REGISTRATIONS registrations"
    }
    val registrations = root.mapIndexed { index, node ->
        parseRegistration(node, "existing registry[$index]")
    }
    validateRegistrations(registrations, "existing registry")
    return registrations
}

private fun parseRegistration(node: JsonNode, path: String): RawClientRegistration {
    node.requireObject(path, STRING_FIELDS + LIST_FIELDS)
    fun string(field: String) =
        node.get(field)?.let { node.requireString(path, field, MAX_JWK_SET) }
    fun list(field: String): List<String>? =
        node.get(field)?.let {
            ensure(it.isArray && it.all { item -> item.isString }) {
                "$path.$field must be an array of strings"
            }
            it.toList().map { item -> item.stringValue() as String }
        }
    return RawClientRegistration(
        clientId = requireNotNull(string("clientId")) { "$path.clientId is required" },
        redirectUris = list("redirectUris").orEmpty(),
        launchUris = list("launchUris").orEmpty(),
        tokenEndpointAuthMethod = string("tokenEndpointAuthMethod"),
        clientSecret = string("clientSecret"),
        jwksUri = string("jwksUri"),
        jwkSet = string("jwkSet"),
        scopes = list("scopes").orEmpty(),
        displayName = string("displayName"),
        teamSlot = string("teamSlot"),
        navn = string("navn"),
        beskrivelse = string("beskrivelse"),
        ikon = string("ikon"),
        launchMode = string("launchMode"),
        grantTypes = list("grantTypes"),
    )
}

/**
 * Validates that [slots] can be added to (or, with [rotate], replaced in) [existing] and returns
 * the registrations that must be kept unchanged.
 */
internal fun registrationsToKeep(
    existing: List<RawClientRegistration>,
    slots: Set<String>,
    rotate: Boolean,
): List<RawClientRegistration> {
    val existingSlots = existing.mapNotNull { it.teamSlot }.toSet()
    if (rotate) {
        val unknown = slots - existingSlots
        ensure(unknown.isEmpty()) { "cannot rotate unknown team(s): ${unknown.sorted()}" }
        slots.forEach { slot ->
            ensure(hasGeneratedShape(existing.filter { it.teamSlot == slot })) {
                "team '$slot' does not have exactly the four generated registrations " +
                    "(public, client_secret_basic, private_key_jwt, client_credentials); " +
                    "refusing a partial rotation"
            }
        }
        return existing.filter { it.teamSlot !in slots }
    }
    val taken = existingSlots.map { it.lowercase() }.toSet()
    val clashing = slots.filter { it.lowercase() in taken }
    ensure(clashing.isEmpty()) {
        "team(s) already registered: ${clashing.sorted()}; use --rotate to replace them"
    }
    ensure(existingSlots.size + slots.size <= MAX_STARTER_TEAMS) {
        "registry would hold more than $MAX_STARTER_TEAMS teams"
    }
    return existing
}

private fun hasGeneratedShape(team: List<RawClientRegistration>): Boolean {
    val (backend, interactive) = team.partition { it.grantTypes == listOf(BACKEND_GRANT) }
    return backend.size == 1 &&
        backend.single().tokenEndpointAuthMethod == "private_key_jwt" &&
        interactive.size == INTERACTIVE_METHODS.size &&
        interactive.all { it.grantTypes == listOf("authorization_code") } &&
        interactive.mapNotNull { it.tokenEndpointAuthMethod }.toSet() == INTERACTIVE_METHODS
}

internal fun validateRegistrations(registrations: List<RawClientRegistration>, label: String) {
    try {
        buildRegistry(registrations)
    } catch (e: IllegalArgumentException) {
        throw CredentialToolException("$label rejected by registry validation: ${e.message}", e)
    }
}
