package no.nav.helse.fhir

import com.google.fhir.model.r4.Boolean
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitioner.repository.PractitionerRepository

class PractitionerServiceTest {

    val practitionerRepository = mockk<PractitionerRepository>()

    val erikPractitionerId = "practitioner-001"
    val erikThePractitioner =
        Practitioner(
            id = erikPractitionerId,
            meta =
                Meta(
                    profile =
                        listOf(
                            Canonical(
                                value =
                                    "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"
                            )
                        )
                ),
            identifier =
                listOf(
                    Identifier(
                        system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
                        value = String(value = "9144889"),
                    )
                ),
            active = Boolean(value = true),
            name =
                listOf(
                    HumanName(
                        family = String(value = "Boom"),
                        given = listOf(String(value = "Carl")),
                        prefix = listOf(String(value = "Dr.")),
                    )
                ),
            gender = Enumeration(value = AdministrativeGender.Male),
            birthDate = Date(value = FhirDate.fromString("1975-06-20")),
        )

    val mariaPractitioner =
        Practitioner(
            id = "practitioner-002",
            meta =
                Meta(
                    profile =
                        listOf(
                            Canonical(
                                value =
                                    "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"
                            )
                        )
                ),
            identifier =
                listOf(
                    Identifier(
                        system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
                        value = String(value = "9144890"),
                    )
                ),
            active = Boolean(value = true),
            name =
                listOf(
                    HumanName(
                        family = String(value = "Mudskipper"),
                        given = listOf(String(value = "Zev")),
                        prefix = listOf(String(value = "Dr.")),
                    )
                ),
            gender = Enumeration(value = AdministrativeGender.Female),
            birthDate = Date(value = FhirDate.fromString("1982-09-14")),
        )

    val andersPractitioner =
        Practitioner(
            id = "practitioner-003",
            meta =
                Meta(
                    profile =
                        listOf(
                            Canonical(
                                value =
                                    "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"
                            )
                        )
                ),
            identifier =
                listOf(
                    Identifier(
                        system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
                        value = String(value = "9144891"),
                    )
                ),
            active = Boolean(value = false),
            name =
                listOf(
                    HumanName(
                        family = String(value = "Andrews"),
                        given = listOf(String(value = "Chris")),
                        prefix = listOf(String(value = "Dr.")),
                    )
                ),
            gender = Enumeration(value = AdministrativeGender.Male),
            birthDate = Date(value = FhirDate.fromString("1968-02-28")),
        )

    @Test
    fun `get practitioner successfully and assert results`() {
        val practitionerService = PractitionerService(practitionerRepository)
        every { practitionerRepository.getPractitioner(any()) } returns erikThePractitioner
        val practitioner = practitionerService.getPractitioner(erikPractitionerId)

        assertEquals(erikThePractitioner.id, practitioner?.id)
        assertEquals(erikThePractitioner.meta, practitioner?.meta)
        assertEquals(erikThePractitioner.identifier, practitioner?.identifier)
        assertEquals(erikThePractitioner.active, practitioner?.active)
        assertEquals(erikThePractitioner.name, practitioner?.name)
        assertEquals(erikThePractitioner.gender, practitioner?.gender)
        assertEquals(erikThePractitioner.birthDate, practitioner?.birthDate)
    }

    @Test
    fun `get practitioner with non existing id should return null`() {
        val practitionerService = PractitionerService(practitionerRepository)
        every { practitionerRepository.getPractitioner(any()) } returns null
        val practitioner = practitionerService.getPractitioner("non-existing-id")

        assertEquals(null, practitioner)
    }

    @Test
    fun `get all practitioners should return all practitioners and assert that there are three practitioners`() {
        val practitionerService = PractitionerService(practitionerRepository)
        every { practitionerRepository.getAllPractitioners() } returns
            listOf(erikThePractitioner, mariaPractitioner, andersPractitioner)
        val practitioners = practitionerService.getAllPractitioners()

        assertEquals(3, practitioners.size)
        assertTrue { practitioners[0].id == erikThePractitioner.id }
    }

    @Test
    fun `get practitioners returns an empty list when there are no practitioners`() {
        val practitionerService = PractitionerService(practitionerRepository)
        every { practitionerRepository.getAllPractitioners() } returns emptyList()
        val practitioners = practitionerService.getAllPractitioners()
        assertTrue { practitioners.isEmpty() }
    }

    @Test
    fun `create practitioner successfully`() {
        val practitionerService = PractitionerService(practitionerRepository)
        val newPractitioner =
            Practitioner(
                id = "practitioner-new",
                meta =
                    Meta(
                        profile =
                            listOf(
                                Canonical(
                                    value =
                                        "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"
                                )
                            )
                    ),
                identifier =
                    listOf(
                        Identifier(
                            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
                            value = String(value = "9144892"),
                        )
                    ),
                active = Boolean(value = true),
                name =
                    listOf(
                        HumanName(
                            family = String(value = "Andrews"),
                            given = listOf(String(value = "Brandon")),
                            prefix = listOf(String(value = "Dr.")),
                        )
                    ),
                gender = Enumeration(value = AdministrativeGender.Male),
                birthDate = Date(value = FhirDate.fromString("1980-03-20")),
            )
        every { practitionerRepository.createPractitioner(any()) } returns newPractitioner

        val created = practitionerService.createPractitioner(newPractitioner)
        verify(exactly = 1) { practitionerRepository.createPractitioner(newPractitioner) }

        assertEquals(newPractitioner.id, created.id)
        assertEquals(newPractitioner.meta, created.meta)
        assertEquals(newPractitioner.identifier, created.identifier)
        assertEquals(newPractitioner.active, created.active)
        assertEquals(newPractitioner.name, created.name)
        assertEquals(newPractitioner.gender, created.gender)
        assertEquals(newPractitioner.birthDate, created.birthDate)
    }
}
