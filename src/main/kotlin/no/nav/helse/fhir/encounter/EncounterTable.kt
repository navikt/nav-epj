package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Encounter
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object EncounterTable : Table("encounter") {
  val id = text("id")
  val data = fhirResource<Encounter>("data")
}
