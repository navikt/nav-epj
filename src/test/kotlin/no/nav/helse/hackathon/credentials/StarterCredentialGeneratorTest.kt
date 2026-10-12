package no.nav.helse.hackathon.credentials

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.nav.helse.smart.security.GrantType
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.buildRegistry
import no.nav.helse.smart.security.parseRegisteredScopes
import org.junit.Test

private val INTERACTIVE = listOf("openid", "launch", "patient/Patient.rs")
private val SYSTEM = listOf("system/Patient.rs")

private fun endpoints(name: String) =
    ClientEndpoints("https://$name.example.com/launch", "https://$name.example.com/callback")

private fun team(slot: String) =
    StarterTeamConfig(slot, endpoints("$slot-p"), endpoints("$slot-s"), endpoints("$slot-k"))

private fun generate(
    teams: List<StarterTeamConfig>,
    interactive: List<String> = INTERACTIVE,
    system: List<String> = SYSTEM,
) = generateStarterCredentials(teams, interactive, system)

private val fixture by lazy { generate((1..MAX_STARTER_TEAMS).map { team("team$it") }) }
private val fixtureClients by lazy { buildRegistry(fixture.registrations) }

class StarterCredentialGeneratorTest {

    @Test
    fun `one team yields four registrations`() {
        val result = generate(listOf(team("a")))
        assertEquals(4, result.registrations.size)
        assertEquals(4, buildRegistry(result.registrations).size)
        assertEquals(1, result.teams.size)
    }

    @Test
    fun `ten teams yield forty independent registrations`() {
        val result = fixture
        assertEquals(40, fixtureClients.size)
        assertEquals(40, fixtureClients.map { it.clientId }.toSet().size)
        assertEquals(10, result.teams.size)
    }

    @Test
    fun `team count outside 1 to 10 is rejected`() {
        assertFailsWith<IllegalArgumentException> { generate(emptyList()) }
        assertFailsWith<IllegalArgumentException> { generate((1..11).map { team("team$it") }) }
    }

    @Test
    fun `blank and duplicate slots are rejected`() {
        assertFailsWith<IllegalArgumentException> { generate(listOf(team(" "))) }
        assertFailsWith<IllegalArgumentException> { generate(listOf(team("a"), team("a"))) }
        for (slot in listOf("../team", "team name", "-team", "a".repeat(65))) {
            assertFailsWith<IllegalArgumentException> { generate(listOf(team(slot))) }
        }
    }

    @Test
    fun `invalid scopes and urls are rejected`() {
        assertFailsWith<IllegalArgumentException> { generate(listOf(team("a")), listOf("bogus")) }
        assertFailsWith<IllegalArgumentException> { generate(listOf(team("a")), emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            generate(listOf(team("a")), system = listOf("patient/Patient.rs"))
        }
        assertFailsWith<IllegalArgumentException> {
            generate(listOf(team("a")), interactive = listOf("system/Patient.rs"))
        }
        val insecure =
            StarterTeamConfig(
                "a",
                ClientEndpoints("http://evil.example.com/l", "https://x.example.com/c"),
                endpoints("s"),
                endpoints("k"),
            )
        assertFailsWith<IllegalArgumentException> { generate(listOf(insecure)) }
        val wildcard =
            StarterTeamConfig(
                "a",
                endpoints("p"),
                ClientEndpoints("https://x.example.com/l", "https://x.example.com/*"),
                endpoints("k"),
            )
        assertFailsWith<IllegalArgumentException> { generate(listOf(wildcard)) }
    }

    @Test
    fun `each team gets the four grant and method variants`() {
        val byTeam = fixtureClients.groupBy { it.teamSlot }
        assertEquals(10, byTeam.size)
        byTeam.values.forEach { clients ->
            val shapes = clients.map {
                Triple(it.tokenEndpointAuthMethod, it.grantTypes, it.allowedScopes.size)
            }
            assertEquals(
                setOf(
                    TokenEndpointAuthMethod.NONE to setOf(GrantType.AUTHORIZATION_CODE),
                    TokenEndpointAuthMethod.CLIENT_SECRET_BASIC to
                        setOf(GrantType.AUTHORIZATION_CODE),
                    TokenEndpointAuthMethod.PRIVATE_KEY_JWT to setOf(GrantType.AUTHORIZATION_CODE),
                    TokenEndpointAuthMethod.PRIVATE_KEY_JWT to setOf(GrantType.CLIENT_CREDENTIALS),
                ),
                shapes.map { it.first to it.second }.toSet(),
            )
        }
        val backend = fixtureClients.filter { GrantType.CLIENT_CREDENTIALS in it.grantTypes }
        assertEquals(10, backend.size)
        backend.forEach {
            assertTrue(it.redirectUris.isEmpty() && it.launchUris.isEmpty())
            assertEquals(parseRegisteredScopes(SYSTEM), it.allowedScopes)
        }
        val interactive = fixtureClients.filter { GrantType.AUTHORIZATION_CODE in it.grantTypes }
        interactive.forEach {
            assertEquals(parseRegisteredScopes(INTERACTIVE), it.allowedScopes)
            assertEquals(1, it.launchUris.size)
            assertEquals(1, it.redirectUris.size)
        }
        assertEquals(
            listOf("https://team1-p.example.com/launch"),
            fixtureClients.first { it.clientId.startsWith("team1-public-") }.launchUris,
        )
    }

    @Test
    fun `client ids carry team and variant`() {
        fixture.teams.forEach { t ->
            listOf(
                    t.publicClientId to "public",
                    t.clientSecret.clientId to "secret",
                    t.launchKey.clientId to "launch-key",
                    t.backendKey.clientId to "backend",
                )
                .forEach { (id, variant) ->
                    assertTrue(id.startsWith("${t.teamSlot}-$variant-"), id)
                }
        }
        val registered = fixtureClients.map { it.clientId }.toSet()
        fixture.teams.forEach {
            assertTrue(it.publicClientId in registered)
            assertTrue(it.clientSecret.clientId in registered)
            assertTrue(it.launchKey.clientId in registered)
            assertTrue(it.backendKey.clientId in registered)
        }
    }

    @Test
    fun `secrets are at least 32 random bytes and unique`() {
        val secrets = fixture.teams.map { it.clientSecret.secret.value }
        assertEquals(10, secrets.toSet().size)
        secrets.forEach { assertTrue(Base64.getUrlDecoder().decode(it).size >= 32) }
        fixture.teams.forEach { t ->
            assertEquals(
                t.clientSecret.secret.value,
                fixtureClients.single { it.clientId == t.clientSecret.clientId }.clientSecret,
            )
        }
    }

    @Test
    fun `registry holds public keys only with unique kids`() {
        val registeredKeys =
            fixtureClients.mapNotNull { it.inlineJwkSet }.flatMap { it.keys }.map { it as RSAKey }
        assertEquals(20, registeredKeys.size)
        assertTrue(registeredKeys.none { it.isPrivate })
        assertTrue(registeredKeys.all { it.algorithm == JWSAlgorithm.RS384 })
        assertTrue(registeredKeys.all { it.size() == 3072 })
        assertEquals(20, registeredKeys.map { it.keyID }.toSet().size)
        assertEquals(20, registeredKeys.map { it.modulus.toString() }.toSet().size)
        fixture.registrations
            .mapNotNull { it.jwkSet }
            .forEach {
                assertFalse(it.contains("\"d\""))
                assertFalse(it.contains("\"p\""))
            }
        fixtureClients
            .filter { it.tokenEndpointAuthMethod != TokenEndpointAuthMethod.PRIVATE_KEY_JWT }
            .forEach { assertNull(it.inlineJwkSet) }
    }

    @Test
    fun `private keys sign RS384 and verify with the registered public key`() {
        fixture.teams.forEach { t ->
            listOf(t.launchKey, t.backendKey).forEach { credential ->
                val privateKey = RSAKey.parse(credential.privateJwk.value)
                assertTrue(privateKey.isPrivate)
                val registered =
                    fixtureClients.single { it.clientId == credential.clientId }.inlineJwkSet!!
                val publicKey = registered.getKeyByKeyId(credential.keyId) as RSAKey
                assertNotNull(publicKey)
                val jws =
                    JWSObject(
                        JWSHeader.Builder(JWSAlgorithm.RS384).keyID(credential.keyId).build(),
                        Payload("hello"),
                    )
                jws.sign(RSASSASigner(privateKey))
                assertTrue(jws.verify(RSASSAVerifier(publicKey)))
            }
            assertTrue(t.launchKey.keyId != t.backendKey.keyId)
        }
    }

    @Test
    fun `default toString does not leak secrets or private keys`() {
        val team = fixture.teams.first()
        val rendered =
            listOf(
                    fixture,
                    team,
                    team.clientSecret,
                    team.launchKey,
                    team.backendKey,
                    team.clientSecret.secret,
                    team.launchKey.privateJwk,
                    team.backendKey.privateJwk,
                    fixture.registrations,
                )
                .joinToString { it.toString() }
        listOf(
                team.clientSecret.secret.value,
                team.launchKey.privateJwk.value,
                team.backendKey.privateJwk.value,
            )
            .forEach { assertFalse(rendered.contains(it)) }
        assertFalse(rendered.contains("\"d\""))
    }
}
