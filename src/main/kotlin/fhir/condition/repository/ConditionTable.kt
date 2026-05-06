package no.nav.helse.fhir.condition.repository

import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Reference
import no.nav.helse.fhir.fhirJsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object ConditionTable : Table("condition") {
    val id = text("id")
    val subject = jsonb<Reference>("subject", fhirJsonConfig)
    val code = jsonb<CodeableConcept>("type", fhirJsonConfig)
}
