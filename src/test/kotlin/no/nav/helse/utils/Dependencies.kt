package no.nav.helse.utils

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.di.dependencies
import io.mockk.mockk
import no.nav.helse.core.DpopConfig
import no.nav.helse.core.Environment
import no.nav.helse.core.EpjConfig
import no.nav.helse.core.PersontjenstenConfig
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
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.parseRegisteredScopes
import no.nav.helse.smart.valkey.ValkeyService
import org.testcontainers.postgresql.PostgreSQLContainer

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
            ),
        valkey = ValkeyConfig("valkey", 8080, false, null, null),
        epj = EpjConfig(baseUrl = "testurl"),
        persontjensten = PersontjenstenConfig(baseUrl = "testurl"),
        dpop =
            DpopConfig(
                helseidTokenAuthUrl = "dsfdsfs",
                clientJwk = "{\n" +
                  "  \"kty\": \"RSA\",\n" +
                  "  \"use\": \"sig\",\n" +
                  "  \"alg\": \"RS256\",\n" +
                  "  \"kid\": \"test-rsa-key-2048\",\n" +
                  "  \"n\": \"sXTdO8GT08gDfJ1MyXJk-Enlw8mTSkLyCa169Xsur1Jy5DfVuhE794W1fNHSK6_dfeC7_AeGuY8npknX4sdPFhmZKd1fHBn0aet8tXZZKo_O9SNFOcH9_RinJ4ObIRWRwC0qwKVICgKxGjjKhJhbbLtune2MJII45XlBVcq0x029hbXKoO_B8ivpoIcz8YXUb5wOJMTQLZ6lcBb5d0GEvBwymb-VHFuvMzygZO5kl9dIIQ4jWSW0fBrQidiOIMy9l-qSnC_PVV18fCBJkrRdJ7kTSCMoyXenCF05a8JH3iJSo3yZ4UyRcHGPqk8WLV52MbdGIKmnkX_XwSPAr3BHDQ\",\n" +
                  "  \"e\": \"AQAB\",\n" +
                  "  \"d\": \"UY6MPvJwply-fX8t00en8BTGbH0wHGk4JAp_AGNkyV7zPsAKkkFpdhEVoBAur7Vb7oZi3yn2WUHZzysQ9UD0sAuDwSHknF2xXxzDbALAaDc_vpo_LpRhzviNmjN6EHKBUflwNKnBuhgSnSKMsPP00Lwz0pFiX0UaMRzRUkSfYRkr5TnPC-ixTVNPOG7BWzqokGUdrj1xkX0s8uYEo93unNvjei3DNy1udjNdIapu6qFTtjBY42ct72xcvYteJmwjo3P34lNq59P_69EZ8ZvC2eeMJVhpIdXkPbjPhat4b_I-6o1Lb9BpjcnMY0hQuOvSlQyFKN7xtqkBWLsz7ubFYQ\",\n" +
                  "  \"p\": \"81vByPSdRpaacm5vZbwhz7ErAnrrO8cXPb3aH5UeE6yaB_lhafCYZWODU6-H4j9dr9chwn9mkiLzVZDN3C1o5BCGYzTRS-gKtjScxF72eGcQBMMePK9Czl7P2CxXpEsmk_ixKbMt6shT9xw0Ln5B2JrKfm2ym1b6jX41U68QNjs\",\n" +
                  "  \"q\": \"uqy5-INkQI8jElPnT2GwaY3YWIl1OJ3jJKchHPpkA_kvWJt2MWJs0bOxyrpeJA_axfw9KfJtmN_32UPnK6Vhc8_x7PzLH9rwqQsOyt-m35rVvuyauj5AfWH6mk7k12zQyCVJCp7j5Ffad3dsQrfplvyVqPzXIuQk8QmkIs0m-1c\",\n" +
                  "  \"dp\": \"QqGk9XjEd0dVEm_aYGiaeVtxA0TUk5F3c5g_2NXjXk-r_PLzESanE1uj4Y32DqR66mJlA_QOCYU4Sv1S4C4uwgMkSJV1mOr3w9uz8LBvm5fccvFZnhP-nrKnBfFeLcXF0k4Nc_VzZQ1ksAww3WoEbWI0f1lZxd6Sy5r6zubn8XE\",\n" +
                  "  \"dq\": \"HTUVS-rS7r72j9GM6YCxZ3KIHSI0sw0RExULO0t6Bp0gaU65qXRq7kydTsjmHeJVKpESyNNjjn3Me7QCIXQY0VQB2ECdT3ikGu7d-6QOqgHB28ONWgKPeI6x9R7O813VNNuQmBbQNVQBnJuU1sOLytHuYKheyNjFZulJQyCyLrU\",\n" +
                  "  \"qi\": \"nSti2m8HxBqq10M_ILWW-_LqJg8rgGpHRQJoI6W1Sg5zdRVegHyrIUriSN522JxzRVzgwaJC4NXYmwzTBGDeByfvzmIIiVt3EArxfar2j_9tRWuuzKH6djU_CY_s5z7zEQbAEeZDWTcbiupZanejkd4l_pRtLCFAUZtDoxthiwc\"\n" +
                  "}",
                clientId = "3131",
            ),
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
            ),
        valkey = ValkeyConfig("valkey", 8080, false, null, null),
        epj = EpjConfig(baseUrl = "testurl"),
        persontjensten = PersontjenstenConfig(baseUrl = "testurl"),
        dpop =
            DpopConfig(
                helseidTokenAuthUrl = "dsfdsfs",
                clientJwk =
                    "{\n" +
                        "  \"kty\": \"RSA\",\n" +
                        "  \"use\": \"sig\",\n" +
                        "  \"alg\": \"RS256\",\n" +
                        "  \"kid\": \"test-rsa-key-1\",\n" +
                        "  \"n\": \"sXchbK0dJm4Yv8w2R1z4m9qP6tU3iN7oL5kH2gF8eD1cB9aM0pQ7rS6tV4wX3yZ2A1bC5dE6fG7hI8jK9lM0nO1pQ2rS3tU4vW5xY6z\",\n" +
                        "  \"e\": \"AQAB\",\n" +
                        "  \"d\": \"V8mY3kL7pQ2wE5rT9uI1oP4aS6dF8gH0jK3lZ5xC7vB9nM2qR4tY6uI8oP1aS3dF5gH7jK9lM0nO2pQ4rS6tU8vW0xY2z\",\n" +
                        "  \"p\": \"8vX3yZ2A1bC5dE6fG7hI8jK9lM0nO1pQ2rS3tU4vW5xY6z7aB8cD9eF0gH1iJ2k\",\n" +
                        "  \"q\": \"7aB8cD9eF0gH1iJ2k3lM4nO5pQ6rS7tU8vW9xY0zA1bC2dE3fG4hI5jK6lM7n\",\n" +
                        "  \"dp\": \"3tU4vW5xY6z7aB8cD9eF0gH1iJ2k3lM4nO5pQ6rS7tU8vW9xY0zA1bC2dE3fG\",\n" +
                        "  \"dq\": \"2rS3tU4vW5xY6z7aB8cD9eF0gH1iJ2k3lM4nO5pQ6rS7tU8vW9xY0zA1bC2dE\",\n" +
                        "  \"qi\": \"1pQ2rS3tU4vW5xY6z7aB8cD9eF0gH1iJ2k3lM4nO5pQ6rS7tU8vW9xY0zA1bC\"\n" +
                        "}",
                clientId = "3131",
            ),
    )

private val clientAssertionVerifier =
    ClientAssertionVerifier(
        env = simpleTestEnvironment,
        jtiStore = valkeyService,
        jwkSetProvider = { ImmutableJWKSet(JWKSet()) },
    )
