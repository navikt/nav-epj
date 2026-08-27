package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Uri
import no.nav.helse.epj.helsepersonell.Helsepersonell
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService

class PractitionerService(val helsepersonellService: HelsepersonellService) {

  suspend fun getPractitioner(practitionerId: PractitionerId): Practitioner? {
    val helsepersonell =
      helsepersonellService.getHelsepersonell(HelsepersonellHpr(practitionerId.value))
    return helsepersonell.toPractitioner()
  }

  fun Helsepersonell.toPractitioner(): Practitioner {
    val givenNames =
      listOf(
        com.google.fhir.model.r4.String(value = this.navn.split(' ').first())
      ) // TODO get dynamically
    val familyName =
      com.google.fhir.model.r4.String(value = this.navn.split(' ').last()) // TODO get dynamically

    return Practitioner(
      id = this.hpr.value,
      name = listOf(HumanName(family = familyName, given = givenNames)),
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner")
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
