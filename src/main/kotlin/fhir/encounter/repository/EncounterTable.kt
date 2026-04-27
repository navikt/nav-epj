package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Reference
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object EncounterTable : Table("encounter") {
  val id = text("id")
  val subject = jsonb<Reference>("subject", jsonConfig)
  val participant = jsonb<Array<Encounter.Participant>>("participant", jsonConfig)
  val diagnosis = jsonb<Array<Encounter.Diagnosis>>("diagnosis", jsonConfig)
  val serviceProvider = jsonb<Reference>("serviceProvider", jsonConfig)
  val status = jsonb<Encounter.EncounterStatus>("status", jsonConfig)
  val type = jsonb<Array<CodeableConcept>>("type", jsonConfig)
  val `class` = jsonb<Coding>("class", jsonConfig)
}

private val jsonConfig = Json { prettyPrint = true }
