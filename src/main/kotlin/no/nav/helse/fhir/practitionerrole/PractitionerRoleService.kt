package no.nav.helse.fhir.practitionerrole

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.PractitionerRole
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.nav.helse.epj.helsepersonell.Helsepersonell

class PractitionerRoleService(private val epjClient: HttpClient) {

  suspend fun getPractitionerRolesByPractitioner(hpr: String): Bundle {
    val httpResponse = epjClient.get("/api/helsepersonell/$hpr")
    if (httpResponse.status.value != 200) {
      return Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))
    }

    val role = httpResponse.body<Helsepersonell>().toPractitionerRole()
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
