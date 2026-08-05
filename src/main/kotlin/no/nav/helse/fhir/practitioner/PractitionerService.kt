package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.Helsepersonell

class PractitionerService(val epjClient: HttpClient) {

  val log = logger()

  suspend fun getPractitioner(practitionerId: PractitionerId): Practitioner? {
    val httpResponse = epjClient.get("/api/helsepersonell/${practitionerId.value}")
    if (httpResponse.status.value == 200) {
      return httpResponse.body<Helsepersonell>().toPractitioner()
    }

    return null // TODO tidy
  }

  fun Helsepersonell.toPractitioner(): Practitioner {
    val givenNames = listOf(com.google.fhir.model.r4.String(value = this.navn.split(' ').first()))
    val familyName = com.google.fhir.model.r4.String(value = this.navn.split(' ').last())

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
