package no.nav.helse.fhir.documentreference.repository

import com.google.fhir.model.r4.DocumentReference

interface DocumentReferenceRepository {
  suspend fun getById(id: String): DocumentReference?

  suspend fun getAll(): List<DocumentReference>

  suspend fun create(documentReference: DocumentReference): DocumentReference
}
