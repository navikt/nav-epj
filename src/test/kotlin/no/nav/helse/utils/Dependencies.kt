package no.nav.helse.utils

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.di.dependencies
import io.mockk.mockk
import no.nav.helse.core.Environment
import no.nav.helse.core.EpjConfig
import no.nav.helse.core.PostgresConfig
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
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.SmartKeys
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.parseRegisteredScopes
import no.nav.helse.smart.valkey.ValkeyService
import org.testcontainers.postgresql.PostgreSQLContainer

private val TEST_PRIVATE_KEY_JWK =
    "{\"kty\": \"RSA\", \"n\": \"yH1vxQ3C8uerjTm9kbb_FsauPaqAi0xjpfeKSrkNGkjF6EECcyYfvKRwJnPUdGy2wg7WjYlWBcSBwe0IClq-g06lHKB-3ToDsbWlQJTHhBeZOX4sAQd3x1xfCzTOnQbBd4yOiTasL4hySjLLf_dcN-yvn2BBEbtNI1PYH9UkRmAwDRu2B4xPpi95IoS1Gz-4xuc1QmkAxeVOrTERJUyZEBDNWfNfbdbQOyXC2kN1xfL6sN92EnIsNr1VK_K627x-VrI9YQ9hHdMBryv3QzcswxzwNtrWEnzWVpFzYYYrTVRjXPMULW9pBS5z5vSruqP_vj1rBbxtFJ4M0sjBtPrv6Q\", \"e\": \"AQAB\", \"d\": \"A0uV1d94h_qJJiHqp5BM7V29nmjaLQVH5qyaEB6uLvOUQSbUY0_J72qlJz9PIMy_95I73N6tfxxwOjTq-r9BaLIXDPs8VTwvXwaKcp8fZQs9laV-aVPrQufAmNQOM07ZnjKSuIGhXzShwENzzHokgLd6BCdrr7QW5qY--U2iXNUPcpwr6Gj-bCMkQIPo5FwWq1idVSsm4kg0x_4L0KTOA343cRB8590p7mm5uov5ssUvQq_3Ff2tLevJW5n97ByvdTdH8o4yuPmOzGpQNteSn71zF8LfzrObHyUFHy1MhvdV2MtHbukrb_gHtD5WF957QcawWP092cKUjSX18VJBwQ\", \"p\": \"4vTh_PCABY6P2_2HhalMuv1O4V--ib2BlKgWKkdf13jeAKx4MhJbsEnTo2Por6Qp7w7AsWJeJCpA34aHYPWn_oq96688OIN3i2-tr7Cbqf_kSWeRDls72bONrqj3u8Nmv80QcIJDOzN0LQYeWvbxSixuALGZISlOq_XBJTA-lFk\", \"q\": \"4iWBkkrXfUbnGMUYGkTWgTqxjnDozHzIPfQdIEvGVi0vzf9K8G7BKhqtEtdZzf7ghuQEcR72uBeqEE-ATJrLmZs3f6Q-W01oT1FUsqQ0kn4fsoYYuf4jC0XHTLwZvEedMhaHmdb_GWMauddIEylcAy8JGQvJ5SFvEldsCn0BBhE\", \"dp\": \"apo5l_RjPbjzy5IvWNHnz6Dxrxyow8v2lJvLJXq3At-r70cTgflrhcd3mDRydCW46KfWTLt2mqgaJqPq7NkWWpJSmOSLdcQSn7UKMQ7UXypp0SzLTqH938jd0N9e1Zv3pDmu8hnNeH43oHpSQcniFkP-O2cwFQpxAIaDfXNGwak\", \"dq\": \"pyrsbfSu-Cc6WyAOohRysBJwAAhMviQbbViPUzlQQpGifdcSUUq9tV8EwyG3e8PFu1DAS9KEtC6iAu6Ru47NpB2N1-fURG-jBMbtIiSpzAQ5cCEaFBrdUs7g9UMyjvAtLkJjOIAqEF0m-2s9FxUCtEPHqOEC_EJLtbYz4kjnQdE\", \"qi\": \"iElEFX4BnV8gzdXeEEc7Bnfy2ZGHrVZd3NCdr6PUhpJe9fTyJW9U8zbI-jhTRyLn3E-IUXL5l9PKqrkbMBA4io2aeCxxkeGbiZvV4Wdt6h3C_mbOo98C2ifir97S1vKDS-ILBRvoiM5RnhclvRIhmdKfKZNkkRTsGbnLVXoBU_M\", \"alg\": \"RS256\", \"use\": \"sig\", \"kid\": \"test-smart-key\"}"

private val valkeyService = mockk<ValkeyService>(relaxed = true)
private val encounterService = mockk<EncounterService>(relaxed = true)
private val patientService = mockk<PatientService>(relaxed = true)

fun Application.configureTestSmartDependencies() {
    configureSerialization()
    dependencies {
        provide<Environment> { simpleTestEnvironment }
        provide<ValkeyService> { valkeyService }
        provide<EncounterService> { encounterService }
        provide<PatientService> { patientService }
        provide<ClientAssertionVerifier> { clientAssertionVerifier }
        provide<SmartKeys> { SmartKeys(TEST_PRIVATE_KEY_JWK) }
    }
    authentication {
        provider("wonderwall-helseid") {
            authenticate { ctx ->
                ctx.principal(HelseIdPrincipal(User(name = "Test", hpr = "111"), DebugInfo("", "")))
            }
        }
    }
    configureSmartRouting()
}

fun createIntegrationEnvironment(postgres: PostgreSQLContainer) =
    Environment(
        postgres =
            PostgresConfig(
                url = "jdbc:${postgres.jdbcUrl.removePrefix("jdbc:")}",
                username = postgres.username,
                password = postgres.password,
            ),
        smart =
            SmartConfig(
                issuerBaseUrl = "http://test/oidc",
                fhirServerUrl = "http://test/fhir",
                clients =
                    listOf(
                        SmartClient(
                            clientId = "test-client-id",
                            redirectUris = listOf("http://test"),
                            launchUris = listOf("http://test/fhir/launch"),
                            tokenEndpointAuthMethod = TokenEndpointAuthMethod.NONE,
                            allowedScopes =
                                parseRegisteredScopes(
                                    listOf("openid", "fhirUser", "launch", "patient/*.cruds")
                                ),
                        )
                    ),
                privateKeyJwk = TEST_PRIVATE_KEY_JWK,
            ),
        valkey = ValkeyConfig("valkey", 8080, false, null, null),
        epj = EpjConfig(baseUrl = "testurl"),
    )

val simpleTestEnvironment =
    Environment(
        postgres = mockk(relaxed = true),
        smart =
            SmartConfig(
                issuerBaseUrl = "http://test/oidc",
                fhirServerUrl = "http://test/fhir",
                clients =
                    listOf(
                        SmartClient(
                            clientId = "test-client-id",
                            redirectUris = listOf("http://test"),
                            launchUris = listOf("http://test/fhir/launch"),
                            tokenEndpointAuthMethod = TokenEndpointAuthMethod.NONE,
                            allowedScopes =
                                parseRegisteredScopes(
                                    listOf("openid", "fhirUser", "launch", "patient/*.cruds")
                                ),
                        )
                    ),
                privateKeyJwk = TEST_PRIVATE_KEY_JWK,
            ),
        valkey = ValkeyConfig("valkey", 8080, false, null, null),
        epj = EpjConfig(baseUrl = "testurl"),
    )

private val clientAssertionVerifier =
    ClientAssertionVerifier(
        env = simpleTestEnvironment,
        jtiStore = valkeyService,
        jwkSetProvider = { ImmutableJWKSet(JWKSet()) },
    )
