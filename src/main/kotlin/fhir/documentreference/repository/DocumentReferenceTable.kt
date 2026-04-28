package no.nav.helse.fhir.documentreference.repository

import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb
import com.google.fhir.model.r4.String as FhirString

object DocumentReferenceTable : Table("document_reference") {
  val id = text("id")
  val status = jsonb<DocumentReferenceStatus>("status", jsonConfig)
  val type = jsonb<CodeableConcept>("type", jsonConfig)
  val description = jsonb<FhirString>("description", jsonConfig)
  val subject = jsonb<Reference>("subject", jsonConfig)
  val author = jsonb<Array<Reference>>("author", jsonConfig)
  val content = jsonb<Array<DocumentReference.Content>>("content", jsonConfig)
  val context = jsonb<DocumentReference.Context>("context", jsonConfig)
}

private val jsonConfig = Json { prettyPrint = true }
