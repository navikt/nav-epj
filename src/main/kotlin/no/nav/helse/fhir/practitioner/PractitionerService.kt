package no.nav.helse.fhir.practitioner

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Uri
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.nav.helse.epj.helsepersonell.Helsepersonell

class PractitionerService(val epjClient: HttpClient) {

  suspend fun getPractitioner(): Practitioner {
    val helsepersonell = epjClient.get("/api/helsepersonell/me").body<Helsepersonell>()

    return helsepersonell.toPractitioner()
  }

  fun Helsepersonell.toPractitioner(): Practitioner {
    return Practitioner(
      id = this.hpr.value,
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
