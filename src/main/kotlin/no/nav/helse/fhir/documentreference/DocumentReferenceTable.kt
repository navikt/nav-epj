package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object DocumentReferenceTable : Table("document_reference") {
  val id = text("id")
  val data = fhirResource<DocumentReference>("data")
}
