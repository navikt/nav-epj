package no.nav.helse.smart.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.util.Base64URL
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

internal const val MAX_STARTER_TEAMS = 10
private const val KEY_BITS = 3072
private const val SECRET_BYTES = 32
private const val PLACEHOLDER_EXPONENT = 65537L
private val SIGNING_ALGORITHM = JWSAlgorithm.RS384
private val TEAM_SLOT_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

internal class ClientEndpoints(val launchUri: String, val callbackUri: String)

internal class StarterTeamConfig(
    val teamSlot: String,
    val publicClient: ClientEndpoints,
    val clientSecretClient: ClientEndpoints,
    val privateKeyJwtClient: ClientEndpoints,
)

internal class SecretValue(val value: String) {
    override fun toString() = "SecretValue(redacted)"
}

internal class ClientSecretCredential(val clientId: String, val secret: SecretValue) {
    override fun toString() = "ClientSecretCredential(clientId=$clientId, secret=redacted)"
}

internal class PrivateKeyCredential(
    val clientId: String,
    val keyId: String,
    val privateJwk: SecretValue,
) {
    override fun toString() = "PrivateKeyCredential(clientId=$clientId, keyId=$keyId, jwk=redacted)"
}

internal class TeamCredentials(
    val teamSlot: String,
    val publicClientId: String,
    val clientSecret: ClientSecretCredential,
    val launchKey: PrivateKeyCredential,
    val backendKey: PrivateKeyCredential,
) {
    override fun toString() = "TeamCredentials(teamSlot=$teamSlot, credentials=redacted)"
}

internal class GeneratedStarterCredentials(
    val registrations: List<RawClientRegistration>,
    val teams: List<TeamCredentials>,
) {
    override fun toString() =
        "GeneratedStarterCredentials(teams=${teams.size}, credentials=redacted)"
}

internal fun generateStarterCredentials(
    teams: List<StarterTeamConfig>,
    interactiveScopes: List<String>,
    systemScopes: List<String>,
): GeneratedStarterCredentials {
    require(teams.size in 1..MAX_STARTER_TEAMS) {
        "starter credentials: team count must be 1..$MAX_STARTER_TEAMS, was ${teams.size}"
    }
    require(teams.all { TEAM_SLOT_PATTERN.matches(it.teamSlot) }) {
        "starter credentials: teamSlot must be 1..64 letters, digits, underscores or hyphens, " +
            "starting with a letter or digit"
    }
    val duplicateSlots = teams.groupingBy { it.teamSlot }.eachCount().filterValues { it > 1 }.keys
    require(duplicateSlots.isEmpty()) {
        "starter credentials: duplicate teamSlot(s): ${duplicateSlots.joinToString()}"
    }

    val placeholderKey = placeholderPublicJwkSet()
    buildRegistry(
        teams.flatMap {
            registrationsFor(
                ids = TeamClientIds.random(it.teamSlot),
                team = it,
                interactiveScopes = interactiveScopes,
                systemScopes = systemScopes,
                clientSecret = "placeholder",
                launchJwkSet = placeholderKey,
                backendJwkSet = placeholderKey,
            )
        }
    )

    val random = SecureRandom()
    val generated = teams.map { team ->
        val ids = TeamClientIds.random(team.teamSlot)
        val launchKey = generateKey()
        val backendKey = generateKey()
        val secret = newSecret(random)
        GeneratedTeam(
            credentials =
                TeamCredentials(
                    teamSlot = team.teamSlot,
                    publicClientId = ids.publicClient,
                    clientSecret = ClientSecretCredential(ids.clientSecret, SecretValue(secret)),
                    launchKey = launchKey.credential(ids.launchKey),
                    backendKey = backendKey.credential(ids.backend),
                ),
            registrations =
                registrationsFor(
                    ids = ids,
                    team = team,
                    interactiveScopes = interactiveScopes,
                    systemScopes = systemScopes,
                    clientSecret = secret,
                    launchJwkSet = launchKey.toPublicSet(),
                    backendJwkSet = backendKey.toPublicSet(),
                ),
        )
    }
    val registrations = generated.flatMap { it.registrations }
    buildRegistry(registrations)
    return GeneratedStarterCredentials(
        registrations = registrations,
        teams = generated.map { it.credentials },
    )
}

private class GeneratedTeam(
    val credentials: TeamCredentials,
    val registrations: List<RawClientRegistration>,
)

private class TeamClientIds(
    val publicClient: String,
    val clientSecret: String,
    val launchKey: String,
    val backend: String,
) {
    companion object {
        fun random(slot: String) =
            TeamClientIds(
                publicClient = clientId(slot, "public"),
                clientSecret = clientId(slot, "secret"),
                launchKey = clientId(slot, "launch-key"),
                backend = clientId(slot, "backend"),
            )

        private fun clientId(slot: String, variant: String) = "$slot-$variant-${UUID.randomUUID()}"
    }
}

private fun registrationsFor(
    ids: TeamClientIds,
    team: StarterTeamConfig,
    interactiveScopes: List<String>,
    systemScopes: List<String>,
    clientSecret: String,
    launchJwkSet: JWKSet,
    backendJwkSet: JWKSet,
): List<RawClientRegistration> {
    fun interactive(clientId: String, endpoints: ClientEndpoints, method: String) =
        RawClientRegistration(
            clientId = clientId,
            launchUris = listOf(endpoints.launchUri),
            redirectUris = listOf(endpoints.callbackUri),
            tokenEndpointAuthMethod = method,
            scopes = interactiveScopes,
            teamSlot = team.teamSlot,
            grantTypes = listOf("authorization_code"),
        )
    return listOf(
        interactive(ids.publicClient, team.publicClient, "none"),
        interactive(ids.clientSecret, team.clientSecretClient, "client_secret_basic")
            .copy(clientSecret = clientSecret),
        interactive(ids.launchKey, team.privateKeyJwtClient, "private_key_jwt")
            .copy(jwkSet = launchJwkSet.toString()),
        RawClientRegistration(
            clientId = ids.backend,
            tokenEndpointAuthMethod = "private_key_jwt",
            jwkSet = backendJwkSet.toString(),
            scopes = systemScopes,
            teamSlot = team.teamSlot,
            grantTypes = listOf("client_credentials"),
        ),
    )
}

private fun generateKey(): RSAKey =
    RSAKeyGenerator(KEY_BITS)
        .keyID(UUID.randomUUID().toString())
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(SIGNING_ALGORITHM)
        .generate()

private fun RSAKey.toPublicSet() = JWKSet(toPublicJWK())

private fun RSAKey.credential(clientId: String) =
    PrivateKeyCredential(clientId, keyID, SecretValue(toJSONString()))

private fun newSecret(random: SecureRandom): String =
    ByteArray(SECRET_BYTES).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

private fun placeholderPublicJwkSet(): JWKSet =
    JWKSet(
        RSAKey.Builder(
                Base64URL.encode(BigInteger.ONE.shiftLeft(KEY_BITS - 1)),
                Base64URL.encode(BigInteger.valueOf(PLACEHOLDER_EXPONENT)),
            )
            .keyID("placeholder")
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(SIGNING_ALGORITHM)
            .build()
    )
