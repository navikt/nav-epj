package no.nav.helse.helseId

import com.nimbusds.jose.JWSAlgorithm
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import no.nav.helse.helseId.HelseIdTestTokens.accessToken
import org.junit.Test

class HelseIdTokenVerifierTest {
    private val verifier = HelseIdTestTokens.verifier()

    @Test
    fun `accepts RS256 token when the same kid is also published as PS256`() = runTest {
        assertEquals("someone", verifier.verify(accessToken(JWSAlgorithm.RS256))?.subject)
    }

    @Test
    fun `accepts PS256 token`() = runTest {
        assertEquals("someone", verifier.verify(accessToken(JWSAlgorithm.PS256))?.subject)
    }

    @Test
    fun `rejects token from another issuer`() = runTest {
        assertNull(verifier.verify(accessToken(iss = "https://evil.example")))
    }
}
