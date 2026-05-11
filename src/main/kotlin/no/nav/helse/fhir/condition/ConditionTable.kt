package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Condition
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object ConditionTable : Table("condition") {
  val id = text("id")
  val data = fhirResource<Condition>("data")
}
