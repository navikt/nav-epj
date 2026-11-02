package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Uri
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.clinic.Clinic
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.clinic.ClinicService

class OrganizationService(val clinicService: ClinicService) {
    val log = logger()

    suspend fun getOrganization(id: OrganizationId, orgNumber: String?): Organization? {
        val clinic = clinicService.getClinic(ClinicId(id.value), orgNumber)
        return clinic.toOrganization()
    }

    fun Clinic.toOrganization(): Organization {
        return Organization(
            id = this.id.value.toString(),
            name = com.google.fhir.model.r4.String(value = this.name),
            meta =
                Meta(
                    profile =
                        listOf(
                            Canonical(
                                value =
                                    "http://hl7.no/fhir/StructureDefinition/no-basis-Organization"
                            )
                        )
                ),
            identifier =
                listOf(
                    Identifier(
                        system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
                        value = com.google.fhir.model.r4.String(value = this.orgNumber),
                    )
                ),
            telecom =
                listOf(
                    ContactPoint(
                        system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
                        value = com.google.fhir.model.r4.String(value = this.phone),
                    )
                ),
        )
    }
}
