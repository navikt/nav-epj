package no.nav.helse.fhir.practitioner.repository

import com.google.fhir.model.r4.Practitioner
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object PractitionerTable : Table("practitioner") {
  val id = text("id")
  val data = fhirResource<Practitioner>("data")
}
