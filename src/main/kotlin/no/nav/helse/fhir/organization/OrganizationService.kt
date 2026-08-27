package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Uri
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.legekontor.LegekontorService

class OrganizationService(val legekontorService: LegekontorService) {
  val log = logger()

  suspend fun getOrganization(id: OrganizationId): Organization? {
    val legekontor = legekontorService.getLegekontor(LegekontorId(id.value))
    return legekontor.toOrganization()
  }

  fun Legekontor.toOrganization(): Organization {
    return Organization(
      id = this.id.value.toString(),
      name = com.google.fhir.model.r4.String(value = this.navn),
      meta =
        Meta(
          profile =
            listOf(
              Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Organization")
            )
        ),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
            value = com.google.fhir.model.r4.String(value = this.id.value.toString()),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.2"),
            value = com.google.fhir.model.r4.String(value = "organisasjonsnummer / HER"),
          ),
        ),
      telecom =
        listOf(
          ContactPoint(
            system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
            value = com.google.fhir.model.r4.String(value = this.tlf),
          )
        ),
    )
  }
}
