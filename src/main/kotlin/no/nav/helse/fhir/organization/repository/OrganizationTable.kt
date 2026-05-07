package no.nav.helse.fhir.organization.repository

import com.google.fhir.model.r4.Organization
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object OrganizationTable : Table("organization") {
  val id = text("id")
  val data = fhirResource<Organization>("data")
}
