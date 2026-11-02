package no.nav.helse.fhir.practitionerrole

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.PractitionerRole
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.clinic.ClinicRepository
import no.nav.helse.epj.helsepersonell.Helsepersonell
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.sessionorg.FastlegelisteRepository

class PractitionerRoleService(
    val helsepersonellService: HelsepersonellService,
    val clinicRepository: ClinicRepository,
    val fastlegelisteRepository: FastlegelisteRepository,
) {

    suspend fun getPractitionerRolesByPractitioner(
        hpr: String,
        sessionOrgNumber: String? = null,
    ): Bundle {
        val helsepersonell = helsepersonellService.getHelsepersonell(HelsepersonellHpr(hpr))

        val organizationId =
            sessionOrgNumber?.let { clinicRepository.findByOrgNumber(it)?.id }
                ?: fastlegelisteRepository.findClinicIdsByHpr(hpr).singleOrNull()
        val role = helsepersonell.toPractitionerRole(organizationId)
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

    private fun Helsepersonell.toPractitionerRole(organizationId: ClinicId?): PractitionerRole =
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
