package no.nav.helse.fhir.organization

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.legekontor.LegekontorService
import org.junit.Test

class OrganizationServiceTest {

    private val legekontorService = mockk<LegekontorService>()
    private val organizationService = OrganizationService(legekontorService)

    @OptIn(ExperimentalUuidApi::class)
    private fun legekontor() =
        Legekontor(
            id = LegekontorId(Uuid.generateV4()),
            navn = "Testlegekontor",
            orgnummer = "123456789",
            tlf = "12345678",
        )

    @Test
    fun `organization has only the ENH organisasjonsnummer identifier`() = runTest {
        val kontor = legekontor()
        coEvery { legekontorService.getLegekontor(kontor.id) } returns kontor

        val organization = organizationService.getOrganization(OrganizationId(kontor.id.value))

        val identifier = organization!!.identifier.single()
        assertEquals("urn:oid:2.16.578.1.12.4.1.4.101", identifier.system?.value)
        assertEquals("123456789", identifier.value?.value)
    }

    @Test
    fun `organization declares the no-basis-Organization profile`() = runTest {
        val kontor = legekontor()
        coEvery { legekontorService.getLegekontor(kontor.id) } returns kontor

        val organization = organizationService.getOrganization(OrganizationId(kontor.id.value))

        assertTrue(
            organization!!.meta!!.profile.any {
                it.value == "http://hl7.no/fhir/StructureDefinition/no-basis-Organization"
            }
        )
    }
}
