@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package no.nav.helse.fhir.practitionerrole

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.epj.clinic.Clinic
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.clinic.ClinicRepository
import no.nav.helse.epj.helsepersonell.Helsepersonell
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.sessionorg.FastlegelisteRepository

class PractitionerRoleServiceTest {
    private val helsepersonellService = mockk<HelsepersonellService>()
    private val clinicRepository = mockk<ClinicRepository>()
    private val fastlegelisteRepository = mockk<FastlegelisteRepository>()
    private val service =
        PractitionerRoleService(helsepersonellService, clinicRepository, fastlegelisteRepository)

    private val sessionOrg = Clinic(ClinicId(Uuid.generateV4()), "Fjordvik", "311111116", null)

    private val helsepersonell = Helsepersonell(HelsepersonellHpr("565501872"), "Test", "LE")

    private fun organizationOf(orgNumber: String?, listed: List<ClinicId> = emptyList()): String? =
        runBlocking {
            coEvery {
                helsepersonellService.getHelsepersonell(HelsepersonellHpr("565501872"))
            } returns helsepersonell
            coEvery { fastlegelisteRepository.findClinicIdsByHpr("565501872") } returns listed
            coEvery { clinicRepository.findByOrgNumber("311111116") } returns sessionOrg
            val role =
                service
                    .getPractitionerRolesByPractitioner("565501872", orgNumber)
                    .entry
                    .single()
                    .resource as com.google.fhir.model.r4.PractitionerRole
            role.organization?.reference?.value
        }

    @Test
    fun `organization comes from the session org`() {
        assertEquals("Organization/${sessionOrg.id.value}", organizationOf("311111116"))
    }

    @Test
    fun `has no organization without a session org or fastlegeliste`() {
        assertNull(organizationOf(null))
    }

    @Test
    fun `uses the single fastlegeliste org without a session org`() {
        val listedId = ClinicId(Uuid.generateV4())
        assertEquals("Organization/${listedId.value}", organizationOf(null, listOf(listedId)))
    }

    @Test
    fun `has no organization with several fastlegeliste orgs and no session org`() {
        val listed = listOf(ClinicId(Uuid.generateV4()), ClinicId(Uuid.generateV4()))
        assertNull(organizationOf(null, listed))
    }
}
