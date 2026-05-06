package no.nav.helse.fhir.documentreference.repository

import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import no.nav.helse.fhir.fhirJsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object DocumentReferenceTable : Table("document_reference") {
  val id = text("id")
  val status = jsonb<DocumentReferenceStatus>("status", fhirJsonConfig)
  val type = jsonb<CodeableConcept>("type", fhirJsonConfig)
  val description = jsonb<FhirString>("description", fhirJsonConfig)
  val subject = jsonb<Reference>("subject", fhirJsonConfig)
  val author = jsonb<Array<Reference>>("author", fhirJsonConfig)
  val content = jsonb<Array<DocumentReference.Content>>("content", fhirJsonConfig)
  val context = jsonb<DocumentReference.Context>("context", fhirJsonConfig)
}
