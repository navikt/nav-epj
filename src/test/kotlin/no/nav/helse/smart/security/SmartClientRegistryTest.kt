package no.nav.helse.smart.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.KeyOperation
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

private fun publicEcJwkJson(kid: String = "ec-kid", curve: Curve = Curve.P_384): String =
    ECKeyGenerator(curve)
        .keyID(kid)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(com.nimbusds.jose.JWSAlgorithm.ES384)
        .generate()
        .toPublicJWK()
        .let { """{"keys":[${it.toJSONString()}]}""" }

/**
 * [YamlConfig] only loads from a `.yaml` file (classpath resource or filesystem path), not from raw
 * content, so tests write the generated document under the project's own `build/` directory (never
 * `/tmp`) and load it from there.
 */
private fun yamlConfig(document: String): ApplicationConfig {
    val dir = File("build/test-smart-client-registry").apply { mkdirs() }
    val file = File.createTempFile("registry-", ".yaml", dir).apply { deleteOnExit() }
    file.writeText(document)
    return requireNotNull(YamlConfig(file.absolutePath)) { "failed to parse generated test yaml" }
}

private fun yamlConfigFor(clientsYaml: String): ApplicationConfig {
    val header =
        """
        smart:
          issuerBaseUrl: "http://test/oidc"
          fhirServerUrl: "http://test/fhir"
          privateKeyJwk: "unused"
          clients:
        """
            .trimIndent()
    val body = clientsYaml.trimIndent().lineSequence().joinToString("\n") { "  $it" }
    return yamlConfig("$header\n$body")
}

class SmartClientRegistryTest {

    @Test
    fun `parses none, client_secret_basic and remote private_key_jwt clients`() {
        val config =
            yamlConfigFor(
                """
                - clientId: "public-app"
                  redirectUris: [ "https://app.example.com/callback" ]
                  launchUris: [ "https://app.example.com/launch" ]
                  tokenEndpointAuthMethod: "none"
                  scopes: [ "openid", "launch" ]
                - clientId: "basic-app"
                  redirectUris: [ "https://basic.example.com/callback" ]
                  launchUris: [ "https://basic.example.com/launch" ]
                  clientSecret: "s3cret"
                  scopes: [ "openid", "launch" ]
                - clientId: "private-app"
                  redirectUris: [ "https://private.example.com/callback" ]
                  launchUris: [ "https://private.example.com/launch" ]
                  tokenEndpointAuthMethod: "private_key_jwt"
                  jwksUri: "https://private.example.com/jwks.json"
                  scopes: [ "openid", "launch" ]
                """
                    .trimIndent()
            )

        val clients = loadSmartClients(config)

        assertEquals(3, clients.size)
        val public = clients.single { it.clientId == "public-app" }
        assertEquals(TokenEndpointAuthMethod.NONE, public.tokenEndpointAuthMethod)
        assertNull(public.clientSecret)

        val basic = clients.single { it.clientId == "basic-app" }
        assertEquals(TokenEndpointAuthMethod.CLIENT_SECRET_BASIC, basic.tokenEndpointAuthMethod)
        assertEquals("s3cret", basic.clientSecret)

        val private = clients.single { it.clientId == "private-app" }
        assertEquals(TokenEndpointAuthMethod.PRIVATE_KEY_JWT, private.tokenEndpointAuthMethod)
        assertEquals("https://private.example.com/jwks.json", private.jwksUri)
        assertNull(private.inlineJwkSet)
    }

    @Test
    fun `parses a private_key_jwt client with an inline public JWK Set`() {
        val jwkSetJson = publicEcJwkJson()
        val config =
            yamlConfigFor(
                """
                - clientId: "local-private-app"
                  redirectUris: [ "http://localhost:3000/callback" ]
                  launchUris: [ "http://localhost:3000/launch" ]
                  tokenEndpointAuthMethod: "private_key_jwt"
                  jwkSet: '$jwkSetJson'
                  scopes: [ "openid", "launch" ]
                """
                    .trimIndent()
            )

        val client = loadSmartClients(config).single()
        assertNull(client.jwksUri)
        assertEquals(1, client.inlineJwkSet?.keys?.size)
        assertEquals("ec-kid", client.inlineJwkSet?.keys?.first()?.keyID)
    }

    @Test
    fun `grant types default to authorization_code`() {
        val client =
            buildRegistry(listOf(RawClientRegistration(clientId = "c", scopes = listOf("openid"))))
                .single()
        assertEquals(setOf(GrantType.AUTHORIZATION_CODE), client.grantTypes)
    }

    @Test
    fun `parses a backend services client from yaml`() {
        val config =
            yamlConfigFor(
                """
                - clientId: "backend"
                  tokenEndpointAuthMethod: "private_key_jwt"
                  jwksUri: "https://backend.example.org/jwks.json"
                  grantTypes: [ "client_credentials" ]
                  scopes: [ "system/Patient.rs" ]
                """
                    .trimIndent()
            )
        val client = loadSmartClients(config).single()
        assertEquals(setOf(GrantType.CLIENT_CREDENTIALS), client.grantTypes)
        assertEquals(TokenEndpointAuthMethod.PRIVATE_KEY_JWT, client.tokenEndpointAuthMethod)
    }

    private fun backend(
        method: String? = "private_key_jwt",
        grants: List<String> = listOf("client_credentials"),
        scopes: List<String> = listOf("system/Patient.rs"),
        secret: String? = null,
        redirects: List<String> = emptyList(),
    ) =
        RawClientRegistration(
            clientId = "backend",
            tokenEndpointAuthMethod = method,
            jwksUri =
                "https://backend.example.org/jwks.json".takeIf { method == "private_key_jwt" },
            clientSecret = secret,
            grantTypes = grants,
            scopes = scopes,
            redirectUris = redirects,
        )

    @Test
    fun `backend services client with valid registration is accepted`() {
        val client = buildRegistry(listOf(backend())).single()
        assertEquals(setOf(GrantType.CLIENT_CREDENTIALS), client.grantTypes)
    }

    @Test
    fun `backend services client rejects invalid combinations`() {
        listOf(
                backend(method = "client_secret_basic", secret = "s"),
                backend(method = "none"),
                backend(scopes = listOf("user/Patient.rs")),
                backend(scopes = listOf("system/Patient.rs", "openid")),
                backend(grants = listOf("client_credentials", "authorization_code")),
                backend(redirects = listOf("https://backend.example.org/cb")),
            )
            .forEach { assertFailsWith<IllegalArgumentException> { buildRegistry(listOf(it)) } }
    }

    @Test
    fun `unknown or empty grant types fail`() {
        val unknown =
            assertFailsWith<IllegalArgumentException> {
                buildRegistry(
                    listOf(
                        RawClientRegistration(
                            clientId = "c",
                            scopes = listOf("openid"),
                            grantTypes = listOf("password"),
                        )
                    )
                )
            }
        assertTrue("'password'" in unknown.message.orEmpty())
        listOf(listOf("password"), emptyList()).forEach {
            assertFailsWith<IllegalArgumentException> {
                buildRegistry(
                    listOf(
                        RawClientRegistration(
                            clientId = "c",
                            scopes = listOf("openid"),
                            grantTypes = it,
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `duplicate client id fails startup`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                buildRegistry(
                    listOf(
                        RawClientRegistration(clientId = "dup", scopes = listOf("openid")),
                        RawClientRegistration(clientId = "dup", scopes = listOf("openid")),
                    )
                )
            }
        assertTrue("duplicate" in ex.message.orEmpty())
    }

    @Test
    fun `private_key_jwt without jwksUri or jwkSet fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        tokenEndpointAuthMethod = "private_key_jwt",
                        scopes = listOf("openid"),
                    )
                )
            )
        }
    }

    @Test
    fun `private_key_jwt with both jwksUri and jwkSet fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        tokenEndpointAuthMethod = "private_key_jwt",
                        jwksUri = "https://example.com/jwks.json",
                        jwkSet = publicEcJwkJson(),
                        scopes = listOf("openid"),
                    )
                )
            )
        }
    }

    @Test
    fun `client_secret_basic without a secret fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        tokenEndpointAuthMethod = "client_secret_basic",
                        scopes = listOf("openid"),
                    )
                )
            )
        }
    }

    @Test
    fun `none client with a registered secret fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        tokenEndpointAuthMethod = "none",
                        clientSecret = "leaked",
                        scopes = listOf("openid"),
                    )
                )
            )
        }
    }

    @Test
    fun `unsupported tokenEndpointAuthMethod fails with a clear message`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                buildRegistry(
                    listOf(
                        RawClientRegistration(
                            clientId = "c",
                            tokenEndpointAuthMethod = "client_secret_post",
                            scopes = listOf("openid"),
                        )
                    )
                )
            }
        assertTrue("unsupported" in ex.message.orEmpty())
    }

    @Test
    fun `wildcard redirect uri fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        redirectUris = listOf("https://example.com/*"),
                        scopes = listOf("openid"),
                    )
                )
            )
        }
    }

    @Test
    fun `redirect and launch uris without a host fail`() {
        listOf("https:/host/callback", "https:///callback").forEach { uri ->
            val ex =
                assertFailsWith<IllegalArgumentException>(uri) {
                    buildRegistry(
                        listOf(
                            RawClientRegistration(
                                clientId = "c",
                                launchUris = listOf(uri),
                                scopes = listOf("openid"),
                            )
                        )
                    )
                }
            assertTrue("without a host" in ex.message.orEmpty(), uri)
        }
    }

    @Test
    fun `insecure non-localhost redirect uri fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        redirectUris = listOf("http://example.com/callback"),
                        scopes = listOf("openid"),
                    )
                )
            )
        }
    }

    @Test
    fun `plain http localhost redirect uri is allowed`() {
        val clients =
            buildRegistry(
                listOf(
                    RawClientRegistration(
                        clientId = "c",
                        redirectUris = listOf("http://localhost:3000/callback"),
                        launchUris = listOf("http://localhost:3000/launch"),
                        scopes = listOf("openid"),
                    )
                )
            )
        assertEquals(1, clients.size)
    }

    @Test
    fun `missing scopes fails`() {
        assertFailsWith<IllegalArgumentException> {
            buildRegistry(listOf(RawClientRegistration(clientId = "c")))
        }
    }

    @Test
    fun `inline jwkSet with private key material is rejected`() {
        val privateJwk =
            ECKeyGenerator(Curve.P_384).keyID("kid").keyUse(KeyUse.SIGNATURE).generate()
        val json = """{"keys":[${privateJwk.toJSONString()}]}"""
        val ex = assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", json) }
        assertTrue("private key material" in ex.message.orEmpty())
    }

    @Test
    fun `inline jwkSet with duplicate kid is rejected`() {
        val key1 = ECKeyGenerator(Curve.P_384).keyID("same").generate().toPublicJWK()
        val key2 = ECKeyGenerator(Curve.P_384).keyID("same").generate().toPublicJWK()
        val json = """{"keys":[${key1.toJSONString()},${key2.toJSONString()}]}"""
        val ex = assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", json) }
        assertTrue("duplicate kid" in ex.message.orEmpty())
    }

    @Test
    fun `inline jwkSet with unsupported curve is rejected`() {
        val key = ECKeyGenerator(Curve.P_256).keyID("kid").generate().toPublicJWK()
        val json = """{"keys":[${key.toJSONString()}]}"""
        assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", json) }
    }

    @Test
    fun `inline jwkSet with encryption use is rejected`() {
        val key =
            RSAKeyGenerator(2048).keyID("kid").keyUse(KeyUse.ENCRYPTION).generate().toPublicJWK()
        val json = """{"keys":[${key.toJSONString()}]}"""
        assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", json) }
    }

    @Test
    fun `inline jwkSet with sign key_ops is rejected`() {
        val key =
            RSAKeyGenerator(2048)
                .keyID("kid")
                .keyOperations(setOf(KeyOperation.SIGN))
                .generate()
                .toPublicJWK()
        val json = """{"keys":[${key.toJSONString()}]}"""
        assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", json) }
    }

    @Test
    fun `inline jwkSet missing kid is rejected`() {
        val key = RSAKeyGenerator(2048).generate().toPublicJWK()
        val json = """{"keys":[${key.toJSONString()}]}"""
        assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", json) }
    }

    @Test
    fun `empty inline jwkSet is rejected`() {
        assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", """{"keys":[]}""") }
    }

    @Test
    fun `malformed inline jwkSet is rejected`() {
        assertFailsWith<IllegalArgumentException> { parsePublicJwkSet("client", "not json") }
    }

    @Test
    fun `secret-backed JSON registry document parses the same shape`() {
        val config =
            yamlConfig(
                """
                smart:
                  issuerBaseUrl: "http://test/oidc"
                  fhirServerUrl: "http://test/fhir"
                  privateKeyJwk: "unused"
                  clientRegistryJson: '[{"clientId":"json-app","redirectUris":["https://app.example.com/callback"],"launchUris":["https://app.example.com/launch"],"tokenEndpointAuthMethod":"none","scopes":["openid"]}]'
                """
                    .trimIndent()
            )

        val clients = loadSmartClients(config)
        assertEquals(1, clients.size)
        assertEquals("json-app", clients.single().clientId)
        assertEquals(TokenEndpointAuthMethod.NONE, clients.single().tokenEndpointAuthMethod)
    }

    @Test
    fun `secret-backed JSON registry parses a backend services client`() {
        val config =
            yamlConfig(
                """
                smart:
                  issuerBaseUrl: "http://test/oidc"
                  fhirServerUrl: "http://test/fhir"
                  privateKeyJwk: "unused"
                  clientRegistryJson: '[{"clientId":"json-backend","tokenEndpointAuthMethod":"private_key_jwt","jwksUri":"https://backend.example.org/jwks.json","grantTypes":["client_credentials"],"scopes":["system/Patient.rs"]}]'
                """
                    .trimIndent()
            )

        val client = loadSmartClients(config).single()
        assertEquals(setOf(GrantType.CLIENT_CREDENTIALS), client.grantTypes)
        assertEquals(TokenEndpointAuthMethod.PRIVATE_KEY_JWT, client.tokenEndpointAuthMethod)
    }

    @Test
    fun `display metadata never exposes secrets or jwk material`() {
        val client =
            SmartClient(
                clientId = "basic-app",
                redirectUris = listOf("https://app.example.com/callback"),
                launchUris = listOf("https://app.example.com/launch"),
                tokenEndpointAuthMethod = TokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
                clientSecret = "top-secret-value",
                allowedScopes = emptySet(),
                teamSlot = "team-01",
            )

        val display = client.toDisplay()
        assertEquals("basic-app", display.clientId)
        assertEquals("team-01", display.teamSlot)
        assertTrue("top-secret-value" !in display.toString())
    }

    @Test
    fun `parses display fields with defaults for a client that has none`() {
        val config =
            yamlConfigFor(
                """
                - clientId: "plain-app"
                  redirectUris: [ "https://plain.example.com/callback" ]
                  launchUris: [ "https://plain.example.com/launch" ]
                  tokenEndpointAuthMethod: "none"
                  scopes: [ "openid", "launch" ]
                - clientId: "shown-app"
                  navn: "Vist app"
                  beskrivelse: "En beskrivelse."
                  ikon: "sykmelding"
                  launchMode: "ask"
                  redirectUris: [ "https://shown.example.com/callback" ]
                  launchUris: [ "https://shown.example.com/launch" ]
                  tokenEndpointAuthMethod: "none"
                  scopes: [ "openid", "launch" ]
                """
                    .trimIndent()
            )

        val (plain, shown) = loadSmartClients(config)

        assertEquals("plain-app", plain.displayName)
        assertEquals("", plain.beskrivelse)
        assertEquals("vindu", plain.ikon)
        assertEquals(LaunchMode.IFRAME, plain.launchMode)
        assertEquals("Vist app", shown.displayName)
        assertEquals("En beskrivelse.", shown.beskrivelse)
        assertEquals("sykmelding", shown.ikon)
        assertEquals(LaunchMode.ASK, shown.launchMode)
    }

    @Test
    fun `rejects an unknown launchMode`() {
        val config =
            yamlConfigFor(
                """
                - clientId: "bad-mode"
                  launchMode: "popup"
                  redirectUris: [ "https://bad.example.com/callback" ]
                  launchUris: [ "https://bad.example.com/launch" ]
                  tokenEndpointAuthMethod: "none"
                  scopes: [ "openid", "launch" ]
                """
                    .trimIndent()
            )

        val error = assertFailsWith<IllegalArgumentException> { loadSmartClients(config) }
        assertTrue("launchMode" in error.message.orEmpty())
        assertTrue("bad-mode" in error.message.orEmpty())
    }

    @Test
    fun `secret-backed JSON registry document carries the display fields`() {
        val config =
            yamlConfig(
                """
                smart:
                  issuerBaseUrl: "http://test/oidc"
                  fhirServerUrl: "http://test/fhir"
                  privateKeyJwk: "unused"
                  clientRegistryJson: '[{"clientId":"json-app","navn":"Json app","launchMode":"tab","ikon":"validator","redirectUris":["https://app.example.com/callback"],"launchUris":["https://app.example.com/launch"],"tokenEndpointAuthMethod":"none","scopes":["openid"]}]'
                """
                    .trimIndent()
            )

        val client = loadSmartClients(config).single()
        assertEquals("Json app", client.displayName)
        assertEquals(LaunchMode.TAB, client.launchMode)
        assertEquals("validator", client.ikon)
    }
}
