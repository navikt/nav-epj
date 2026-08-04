package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.legekontor.Legekontor

class OrganizationService(private val epjClient: HttpClient) {
  val log = logger()

  suspend fun getOrganization(id: OrganizationId): Organization {
    val legekontor = epjClient.get("api/legekontor/$id").body<Legekontor>()
    return legekontor.toOrganization()
  }

  fun Legekontor.toOrganization(): Organization {
    return Organization(
      id = this.id.value.toString(),
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
            value = com.google.fhir.model.r4.String(this.id.value.toString()),
          ),
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.2"),
            value = com.google.fhir.model.r4.String("organisasjonsnummer / HER"),
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
