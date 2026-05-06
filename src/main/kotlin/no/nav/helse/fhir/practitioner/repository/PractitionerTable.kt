package no.nav.helse.fhir.practitioner.repository

import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import no.nav.helse.fhir.fhirJsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object PractitionerTable : Table("practitioner") {
    val id = text("id")
    val meta = jsonb<Meta>("meta", fhirJsonConfig)
    val identifier = jsonb<Array<Identifier>>("identifier", fhirJsonConfig)
    val active = bool("active")
    val name = jsonb<Array<HumanName>>("name", fhirJsonConfig)
    val gender = jsonb<AdministrativeGender>("gender", fhirJsonConfig)
    val birthDate = jsonb<Date>("birth_date", fhirJsonConfig)
}
