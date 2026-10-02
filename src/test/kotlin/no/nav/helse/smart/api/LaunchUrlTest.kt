package no.nav.helse.smart.api

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchUrlTest {
    @Test
    fun `appends iss and launch after a trailing slash`() {
        assertEquals(
            "https://app.test/fhir/launch/?iss=https%3A%2F%2Fepj.test%2Ffhir&launch=id-1",
            buildLaunchUrl("https://app.test/fhir/launch", "https://epj.test/fhir", "id-1"),
        )
    }

    @Test
    fun `keeps an existing trailing slash`() {
        assertEquals(
            "https://app.test/launch/?iss=x&launch=id-1",
            buildLaunchUrl("https://app.test/launch/", "x", "id-1"),
        )
    }

    @Test
    fun `keeps a query string on the launch uri`() {
        assertEquals(
            "https://app.test/launch/?tenant=a+b&iss=x&launch=id-1",
            buildLaunchUrl("https://app.test/launch?tenant=a+b", "x", "id-1"),
        )
    }

    @Test
    fun `encodes reserved characters in iss`() {
        assertEquals(
            "https://app.test/launch/?iss=https%3A%2F%2Fepj.test%2Ffhir%3Fa%3D1%26b%3D2&launch=id-1",
            buildLaunchUrl("https://app.test/launch", "https://epj.test/fhir?a=1&b=2", "id-1"),
        )
    }
}
