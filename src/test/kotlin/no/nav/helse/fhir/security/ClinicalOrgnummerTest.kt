package no.nav.helse.fhir.security

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import org.junit.Test

class ClinicalOrgnummerTest {
    private fun principal(context: ScopeContext, orgnummer: String? = null) =
        SmartPrincipal(
            subject = "s",
            scopes = setOf(SmartScope.Fhir(context, "Encounter", setOf(Interaction.READ))),
            patient = null,
            encounter = null,
            orgnummer = orgnummer,
        )

    @Test
    fun `a token with an orgnr is restricted to it`() {
        assertEquals("311111116", principal(ScopeContext.USER, "311111116").clinicalOrgnummer())
    }

    @Test
    fun `a system token without an orgnr is unrestricted`() {
        assertNull(principal(ScopeContext.SYSTEM).clinicalOrgnummer())
    }

    @Test
    fun `a user token without an orgnr is rejected`() {
        assertFailsWith<OrganizationContextMissingException> {
            principal(ScopeContext.USER).clinicalOrgnummer()
        }
    }

    @Test
    fun `creating requires an orgnr`() {
        assertFailsWith<OrganizationContextMissingException> {
            principal(ScopeContext.SYSTEM).requireClinicalOrgnummer()
        }
    }
}
