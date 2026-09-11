package no.nav.helse.helseid

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.jackson3.jackson
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.DpopConfig
import no.nav.helse.core.Environment
import no.nav.helse.core.EpjConfig
import no.nav.helse.core.PersontjenstenConfig
import no.nav.helse.core.SmartConfig
import no.nav.helse.core.ValkeyConfig
import no.nav.helse.helseId.DpopClient
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.parseRegisteredScopes
import org.junit.Test

internal class DpopClientTest {

    @Test
    fun `call helseidtoken endpoint get accestoken and dpop prof`() = runTest {
        val env =
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
                                            listOf(
                                                "openid",
                                                "fhirUser",
                                                "launch",
                                                "patient/*.cruds",
                                            )
                                        ),
                                )
                            ),
                    ),
                valkey = ValkeyConfig("valkey", 8080, false, null, null),
                epj = EpjConfig(baseUrl = "testurl"),
                persontjensten = PersontjenstenConfig(baseUrl = "testurl"),
                dpop =
                    DpopConfig(
                        helseidTokenAuthUrl = "http:localhost:8080/connect/token", // https://helseid-sts.test.nhn.no/connect/token
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
                          "}", // TODO READ FROM env
                        clientId = "1232131", // TODO READ FROM env
                    ),
            )

        val httpClient = HttpClient {
            install(Logging)
            install(ContentNegotiation) { jackson {} }
        }

        val dpopClient = DpopClient(httpClient, env)
        // used for local testing
        dpopClient.getDpopProfAndAccesToken()

        // assertNotNull(dpopPRofAndAccessToken)
    }
}
