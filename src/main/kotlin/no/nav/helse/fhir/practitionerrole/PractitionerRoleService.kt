package no.nav.helse.fhir.practitionerrole

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.PractitionerRole
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import no.nav.helse.epj.helsepersonell.Helsepersonell
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService

class PractitionerRoleService(val helsepersonellService: HelsepersonellService) {

  suspend fun getPractitionerRolesByPractitioner(hpr: String): Bundle {
    val helsepersonell = helsepersonellService.getHelsepersonell(HelsepersonellHpr(hpr))

    val role = helsepersonell.toPractitionerRole()
    return Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      entry =
        listOf(Bundle.Entry(fullUrl = Uri(value = "PractitionerRole/${role.id}"), resource = role)),
    )
  }

  private fun Helsepersonell.toPractitionerRole(): PractitionerRole =
    PractitionerRole(
      id = this.hpr.value,
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-PractitionerRole")
            )
        ),
      practitioner =
        Reference(
          reference = com.google.fhir.model.r4.String(value = "Practitioner/${this.hpr.value}")
        ),
      organization =
        Reference(
          reference =
            com.google.fhir.model.r4.String(value = "Organization/${this.legekontorId.value}")
        ),
    )
}
