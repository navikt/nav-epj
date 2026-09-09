package no.nav.helse.epj.persontjensten

import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class PersontjenstenHttpClientTest {

    @Test
    fun `call persontjensten with valid token`() = runTest {
        val baseurl = "https://et.persontjenesten.test.nhn.no/api/v3"
        val dpopToken = "wrong"
        val fnr = "17718407281"

        val persontjenstenHttpClient =
            PersontjenstenHttpClient(baseUrl = baseurl, dpopToken = dpopToken)

        val personWithName = persontjenstenHttpClient.getByNin(fnr)

        // assertEquals("Per", personWithName?.givenName)

    }
}
