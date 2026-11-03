package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner as FhirPractitioner
import com.google.fhir.model.r4.Uri
import no.nav.helse.ehr.practitioner.Practitioner
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.ehr.practitioner.PractitionerService as EhrPractitionerService

class PractitionerService(val practitionerService: EhrPractitionerService) {

    suspend fun getPractitioner(practitionerId: PractitionerId): FhirPractitioner? {
        val practitioner =
            practitionerService.getPractitioner(PractitionerHpr(practitionerId.value))
        return practitioner.toPractitioner()
    }

    fun Practitioner.toPractitioner(): FhirPractitioner {
        val givenNames =
            listOf(
                com.google.fhir.model.r4.String(value = this.name.split(' ').first())
            ) // TODO get dynamically
        val familyName =
            com.google.fhir.model.r4.String(
                value = this.name.split(' ').last()
            ) // TODO get dynamically

        return FhirPractitioner(
            id = this.hpr.value,
            name = listOf(HumanName(family = familyName, given = givenNames)),
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
                        value = com.google.fhir.model.r4.String(value = this.hpr.value),
                    )
                ),
        )
    }
}
