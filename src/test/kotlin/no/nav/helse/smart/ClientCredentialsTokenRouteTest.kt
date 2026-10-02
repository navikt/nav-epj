package no.nav.helse.smart

import com.auth0.jwt.JWT
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.nav.helse.core.Environment
import no.nav.helse.core.EpjConfig
import no.nav.helse.core.SmartConfig
import no.nav.helse.core.ValkeyConfig
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.smart.api.configureSmartRouting
import no.nav.helse.smart.security.ClientAssertionVerifier
import no.nav.helse.smart.security.GrantType
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.SmartKeys
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.codeChallengeS256
import no.nav.helse.smart.security.parseRegisteredScopes
import no.nav.helse.smart.valkey.AuthCodeContext
import no.nav.helse.smart.valkey.LaunchContext
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.utils.TEST_PRIVATE_KEY_JWK
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private const val BACKEND_CLIENT_ID = "backend-client"
private const val CODE_CLIENT_ID = "code-client"
private const val ISSUER = "http://test/oidc"
private const val FHIR_URL = "http://test/fhir"
private const val ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

class ClientCredentialsTokenRouteTest {
    private val key = ECKeyGenerator(Curve.P_384).keyID("backend-kid").generate()
    private val smartKeys = SmartKeys(TEST_PRIVATE_KEY_JWK)
    private val mapper = jacksonObjectMapper()

    private val clients =
        listOf(
            SmartClient(
                clientId = BACKEND_CLIENT_ID,
                redirectUris = emptyList(),
                launchUris = emptyList(),
                tokenEndpointAuthMethod = TokenEndpointAuthMethod.PRIVATE_KEY_JWT,
                inlineJwkSet = JWKSet(key.toPublicJWK()),
                allowedScopes =
                    parseRegisteredScopes(listOf("system/Patient.rs", "system/Observation.read")),
                grantTypes = setOf(GrantType.CLIENT_CREDENTIALS),
            ),
            SmartClient(
                clientId = CODE_CLIENT_ID,
                redirectUris = listOf("http://test"),
                launchUris = listOf("http://test/fhir/launch"),
                tokenEndpointAuthMethod = TokenEndpointAuthMethod.PRIVATE_KEY_JWT,
                inlineJwkSet = JWKSet(key.toPublicJWK()),
                allowedScopes = parseRegisteredScopes(listOf("openid", "patient/*.cruds")),
            ),
        )

    private val env =
        Environment(
            postgres = mockk(relaxed = true),
            smart =
                SmartConfig(
                    issuerBaseUrl = ISSUER,
                    fhirServerUrl = FHIR_URL,
                    clients = clients,
                    privateKeyJwk = TEST_PRIVATE_KEY_JWK,
                ),
            valkey = ValkeyConfig("valkey", 8080, false, null, null),
            epj = EpjConfig(baseUrl = "testurl"),
        )

    private val valkey =
        mockk<ValkeyService>(relaxed = true).also {
            coEvery { it.setIfAbsent(any(), any(), any()) } returns true
        }

    private fun Application.testModule() {
        configureSerialization()
        dependencies {
            provide<Environment> { env }
            provide<ValkeyService> { valkey }
            provide<EncounterService> { mockk(relaxed = true) }
            provide<PatientService> { mockk(relaxed = true) }
            provide<SmartKeys> { smartKeys }
            provide<ClientAssertionVerifier> {
                ClientAssertionVerifier(
                    env = env,
                    jtiStore = valkey,
                    jwkSetProvider = { throw AssertionError("unexpected jwks fetch") },
                )
            }
        }
        authentication {
            provider("wonderwall-helseid") {
                authenticate { ctx ->
                    ctx.principal(
                        HelseIdPrincipal(User(name = "Test", hpr = "111"), DebugInfo("", ""))
                    )
                }
            }
        }
        configureSmartRouting()
    }

    private fun assertion(
        clientId: String = BACKEND_CLIENT_ID,
        signingKey: com.nimbusds.jose.jwk.ECKey = key,
        aud: String = "$ISSUER/token",
        expiresInMillis: Long = 60_000,
        jti: String = UUID.randomUUID().toString(),
    ): String {
        val header =
            JWSHeader.Builder(JWSAlgorithm.ES384)
                .type(JOSEObjectType.JWT)
                .keyID(signingKey.keyID)
                .build()
        val claims =
            JWTClaimsSet.Builder()
                .issuer(clientId)
                .subject(clientId)
                .audience(aud)
                .expirationTime(Date(System.currentTimeMillis() + expiresInMillis))
                .jwtID(jti)
                .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(signingKey)) }.serialize()
    }

    private fun tokenRequest(
        scope: String? = "system/Patient.rs",
        assertion: String? = assertion(),
        grantType: String = "client_credentials",
        clientId: String? = null,
        block: (ParametersBuilder) -> Unit = {},
    ) = Parameters.build {
        append("grant_type", grantType)
        scope?.let { append("scope", it) }
        clientId?.let { append("client_id", it) }
        assertion?.let {
            append("client_assertion_type", ASSERTION_TYPE)
            append("client_assertion", it)
        }
        block(this)
    }

    private fun token(block: suspend (suspend (Parameters) -> HttpResponse) -> Unit) =
        testApplication {
            application { testModule() }
            block { params ->
                client.post("/oidc/token") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(params.formUrlEncode())
                }
            }
        }

    private fun body(text: String): Map<String, Any?> = mapper.readValue(text)

    @Test
    fun `valid client assertion returns a patient-less access token`() = token { post ->
        val response = post(tokenRequest(scope = "system/Patient.rs system/Observation.read"))

        assertEquals(HttpStatusCode.OK, response.status)
        val json = body(response.bodyAsText())
        assertEquals("Bearer", json["token_type"])
        assertEquals(300, json["expires_in"])
        assertEquals("system/Patient.rs system/Observation.read", json["scope"])
        assertEquals(false, json["need_patient_banner"])
        for (absent in listOf("patient", "encounter", "id_token", "refresh_token")) {
            assertFalse(json.containsKey(absent), "$absent must be omitted")
        }

        val jwt =
            JWT.require(smartKeys.algorithm)
                .withIssuer(ISSUER)
                .withAudience(FHIR_URL)
                .build()
                .verify(json["access_token"] as String)
        assertEquals("at+jwt", jwt.type)
        assertEquals(BACKEND_CLIENT_ID, jwt.subject)
        assertEquals("system/Patient.rs system/Observation.read", jwt.getClaim("scope").asString())
        assertTrue(jwt.expiresAt.time - jwt.issuedAt.time <= 300_000)
        assertTrue(jwt.getClaim("patient").isMissing)
        assertTrue(jwt.getClaim("encounter").isMissing)
    }

    @Test
    fun `client_credentials is handled without authorization code parameters`() = token { post ->
        assertEquals(HttpStatusCode.OK, post(tokenRequest()).status)
    }

    @Test
    fun `client registered only for authorization_code is rejected`() = token { post ->
        val response =
            post(tokenRequest(assertion = assertion(clientId = CODE_CLIENT_ID), scope = "openid"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("unauthorized_client", body(response.bodyAsText())["error"])
    }

    @Test
    fun `unknown client is rejected`() = token { post ->
        val response = post(tokenRequest(assertion = assertion(clientId = "nobody")))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_client", body(response.bodyAsText())["error"])
    }

    @Test
    fun `assertion signed with another key is rejected`() = token { post ->
        val other = ECKeyGenerator(Curve.P_384).keyID("backend-kid").generate()
        val response = post(tokenRequest(assertion = assertion(signingKey = other)))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("invalid_client", body(response.bodyAsText())["error"])
    }

    @Test
    fun `assertion with wrong audience is rejected`() = token { post ->
        val response = post(tokenRequest(assertion = assertion(aud = "http://other/token")))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `expired assertion is rejected`() = token { post ->
        val response = post(tokenRequest(assertion = assertion(expiresInMillis = -1_000)))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("invalid_client", body(response.bodyAsText())["error"])
    }

    @Test
    fun `missing client assertion is rejected`() = token { post ->
        val response = post(tokenRequest(assertion = null, clientId = BACKEND_CLIENT_ID))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("invalid_client", body(response.bodyAsText())["error"])
    }

    @Test
    fun `replayed assertion is rejected`() = token { post ->
        coEvery { valkey.setIfAbsent(any(), any(), any()) } returnsMany listOf(true, false)
        val replayed = assertion()

        assertEquals(HttpStatusCode.OK, post(tokenRequest(assertion = replayed)).status)
        assertEquals(HttpStatusCode.Unauthorized, post(tokenRequest(assertion = replayed)).status)
    }

    @Test
    fun `missing scope is rejected as invalid_request`() = token { post ->
        val response = post(tokenRequest(scope = null))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_request", body(response.bodyAsText())["error"])
    }

    @Test
    fun `non system and malformed scopes are rejected as invalid_scope`() {
        val rejected =
            listOf(
                "patient/Patient.rs",
                "user/Patient.rs",
                "openid",
                "launch",
                "system/Patient",
                "system/Patient.zz",
                "system/.rs",
                "system/Patient.rs bogus",
                "system/Patient.rs  system/Observation.read",
                " system/Patient.rs",
                "",
            )
        for (scope in rejected) {
            token { post ->
                val response = post(tokenRequest(scope = scope))

                assertEquals(HttpStatusCode.BadRequest, response.status, "scope '$scope'")
                assertEquals(
                    "invalid_scope",
                    body(response.bodyAsText())["error"],
                    "scope '$scope'",
                )
            }
        }
    }

    @Test
    fun `scope outside the registration is rejected and not narrowed`() {
        for (scope in
            listOf(
                "system/Encounter.rs",
                "system/Patient.cruds",
                "system/*.rs",
                "system/Patient.rs system/Encounter.rs",
            )) {
            token { post ->
                val response = post(tokenRequest(scope = scope))

                assertEquals(HttpStatusCode.BadRequest, response.status, "scope '$scope'")
                assertEquals(
                    "invalid_scope",
                    body(response.bodyAsText())["error"],
                    "scope '$scope'",
                )
            }
        }
    }

    @Test
    fun `authorization_code exchange still issues patient and encounter claims`() = token { post ->
        val verifier = "code-verifier"
        coEvery { valkey.getAndDeleteAuthCode("auth-code") } returns
            AuthCodeContext(
                username = "Test",
                redirectUrl = "http://test",
                launch = LaunchContext("patient-1", "encounter-1", "111"),
                subject = "111",
                scope = "openid launch patient/*.cruds",
                clientId = CODE_CLIENT_ID,
                codeChallenge = codeChallengeS256(verifier),
            )

        val response =
            post(
                tokenRequest(scope = null, assertion = assertion(clientId = CODE_CLIENT_ID)) {
                    it.append("code", "auth-code")
                    it.append("redirect_uri", "http://test")
                    it.append("code_verifier", verifier)
                    it.set("grant_type", "authorization_code")
                }
            )

        assertEquals(HttpStatusCode.OK, response.status)
        val json = body(response.bodyAsText())
        assertEquals(3600, json["expires_in"])
        assertEquals("openid launch patient/*.cruds", json["scope"])
        assertEquals("patient-1", json["patient"])
        assertEquals("encounter-1", json["encounter"])
        assertEquals(true, json["need_patient_banner"])
        assertTrue(json["id_token"] is String)
        assertFalse(json.containsKey("refresh_token"))

        val jwt =
            JWT.require(smartKeys.algorithm)
                .withIssuer(ISSUER)
                .withAudience(FHIR_URL)
                .build()
                .verify(json["access_token"] as String)
        assertEquals("at+jwt", jwt.type)
        assertEquals("111", jwt.subject)
        assertEquals("patient-1", jwt.getClaim("patient").asString())
        assertEquals("encounter-1", jwt.getClaim("encounter").asString())
        assertEquals(3_600_000L, jwt.expiresAt.time - jwt.issuedAt.time)
    }

    @Test
    fun `authorization_code grant without code still returns invalid_request`() = token { post ->
        val response = post(Parameters.build { append("grant_type", "authorization_code") })

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_request", body(response.bodyAsText())["error"])
        assertNull(body(response.bodyAsText())["access_token"])
    }
}
