package no.nav.helse.fhir.encounter.repository

import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Reference
import no.nav.helse.fhir.fhirJsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object EncounterTable : Table("encounter") {
    val id = text("id")
    val subject = jsonb<Reference>("subject", fhirJsonConfig)
    val participant = jsonb<Array<Encounter.Participant>>("participant", fhirJsonConfig)
    val diagnosis = jsonb<Array<Encounter.Diagnosis>>("diagnosis", fhirJsonConfig)
    val serviceProvider = jsonb<Reference>("serviceProvider", fhirJsonConfig)
    val status = jsonb<Encounter.EncounterStatus>("status", fhirJsonConfig)
    val type = jsonb<Array<CodeableConcept>>("type", fhirJsonConfig)
    val `class` = jsonb<Coding>("class", fhirJsonConfig)
}
