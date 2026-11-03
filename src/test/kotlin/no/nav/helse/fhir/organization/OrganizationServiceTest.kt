package no.nav.helse.fhir.organization

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.ehr.clinic.Clinic
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.clinic.ClinicService
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class OrganizationServiceTest {

    private val clinicService = mockk<ClinicService>()
    private val organizationService = OrganizationService(clinicService)

    @OptIn(ExperimentalUuidApi::class)
    private fun clinic() =
        Clinic(
            id = ClinicId(Uuid.generateV4()),
            name = "Testlegekontor",
            orgNumber = "123456789",
            phone = "12345678",
        )

    @Test
    fun `organization has only the ENH organisasjonsnummer identifier`() = runTest {
        val clinic = clinic()
        coEvery { clinicService.getClinic(clinic.id, DEFAULT_ORG_NUMBER) } returns clinic

        val organization =
            organizationService.getOrganization(OrganizationId(clinic.id.value), DEFAULT_ORG_NUMBER)

        val identifier = organization!!.identifier.single()
        assertEquals("urn:oid:2.16.578.1.12.4.1.4.101", identifier.system?.value)
        assertEquals("123456789", identifier.value?.value)
    }

    @Test
    fun `organization declares the no-basis-Organization profile`() = runTest {
        val clinic = clinic()
        coEvery { clinicService.getClinic(clinic.id, DEFAULT_ORG_NUMBER) } returns clinic

        val organization =
            organizationService.getOrganization(OrganizationId(clinic.id.value), DEFAULT_ORG_NUMBER)

        assertTrue(
            organization!!.meta!!.profile.any {
                it.value == "http://hl7.no/fhir/StructureDefinition/no-basis-Organization"
            }
        )
    }
}
