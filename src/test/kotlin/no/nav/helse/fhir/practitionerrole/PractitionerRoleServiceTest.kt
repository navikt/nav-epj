@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package no.nav.helse.fhir.practitionerrole

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.epj.helsepersonell.Helsepersonell
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.legekontor.LegekontorRepository

class PractitionerRoleServiceTest {
    private val helsepersonellService = mockk<HelsepersonellService>()
    private val legekontorRepository = mockk<LegekontorRepository>()
    private val service = PractitionerRoleService(helsepersonellService, legekontorRepository)

    private val sessionOrg =
        Legekontor(LegekontorId(Uuid.generateV4()), "Fjordvik", "311111116", null)
    private val storedOrgId = LegekontorId(Uuid.generateV4())

    private fun helsepersonell(legekontorId: LegekontorId?) =
        Helsepersonell(HelsepersonellHpr("565501872"), legekontorId, "Test", "LE")

    private fun organizationOf(orgnummer: String?, legekontorId: LegekontorId?): String? =
        runBlocking {
            coEvery {
                helsepersonellService.getHelsepersonell(HelsepersonellHpr("565501872"))
            } returns helsepersonell(legekontorId)
            coEvery { legekontorRepository.findByOrgnummer("311111116") } returns sessionOrg
            val role =
                service
                    .getPractitionerRolesByPractitioner("565501872", orgnummer)
                    .entry
                    .single()
                    .resource as com.google.fhir.model.r4.PractitionerRole
            role.organization?.reference?.value
        }

    @Test
    fun `organization comes from the session org rather than the stored legekontor`() {
        assertEquals(
            "Organization/${sessionOrg.id.value}",
            organizationOf("311111116", storedOrgId),
        )
    }

    @Test
    fun `organization comes from the session org when the clinician has no stored legekontor`() {
        assertEquals("Organization/${sessionOrg.id.value}", organizationOf("311111116", null))
    }

    @Test
    fun `falls back to the stored legekontor without a session org`() {
        assertEquals("Organization/${storedOrgId.value}", organizationOf(null, storedOrgId))
    }

    @Test
    fun `has no organization without a session org or stored legekontor`() {
        assertNull(organizationOf(null, null))
    }
}
