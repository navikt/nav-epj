package no.nav.helse.fhir.practitionerrole

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.PractitionerRole
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.clinic.ClinicRepository
import no.nav.helse.ehr.practitioner.Practitioner
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.ehr.practitioner.PractitionerService
import no.nav.helse.ehr.sessionorg.FastlegelisteRepository

class PractitionerRoleService(
    val practitionerService: PractitionerService,
    val clinicRepository: ClinicRepository,
    val fastlegelisteRepository: FastlegelisteRepository,
) {

    suspend fun getPractitionerRolesByPractitioner(
        hpr: String,
        sessionOrgNumber: String? = null,
    ): Bundle {
        val practitioner = practitionerService.getPractitioner(PractitionerHpr(hpr))

        val organizationId =
            sessionOrgNumber?.let { clinicRepository.findByOrgNumber(it)?.id }
                ?: fastlegelisteRepository.findClinicIdsByHpr(hpr).singleOrNull()
        val role = practitioner.toPractitionerRole(organizationId)
        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                listOf(
                    Bundle.Entry(
                        fullUrl = Uri(value = "PractitionerRole/${role.id}"),
                        resource = role,
                    )
                ),
        )
    }

    private fun Practitioner.toPractitionerRole(organizationId: ClinicId?): PractitionerRole =
        PractitionerRole(
            id = this.hpr.value,
            meta =
                Meta(
                    profile =
                        listOf(
                            Canonical(
                                value =
                                    "http://hl7.no/fhir/StructureDefinition/no-basis-PractitionerRole"
                            )
                        )
                ),
            practitioner =
                Reference(
                    reference =
                        com.google.fhir.model.r4.String(value = "Practitioner/${this.hpr.value}")
                ),
            organization =
                organizationId?.let {
                    Reference(
                        reference =
                            com.google.fhir.model.r4.String(value = "Organization/${it.value}")
                    )
                },
        )
}
