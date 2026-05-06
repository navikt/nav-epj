package no.nav.helse.fhir.organization.repository

import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import no.nav.helse.fhir.fhirJsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object OrganizationTable : Table("organization") {
  val id = text("id")
  val meta = jsonb<Meta>("meta", fhirJsonConfig)
  val identifier = jsonb<Array<Identifier>>("identifier", fhirJsonConfig)
  val telecom = jsonb<Array<ContactPoint>>("telecom", fhirJsonConfig)
}
