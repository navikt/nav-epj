package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import no.nav.helse.fhir.questionnaireresponse.UpsertResult

class DocumentReferenceService(private val repository: DocumentReferenceRepository) {

  suspend fun getDocumentReference(id: String): DocumentReference? {
    return repository.getById(id)
  }

  suspend fun getAllDocumentReferences(): List<DocumentReference> {
    return repository.getAll()
  }

  suspend fun createDocumentReference(documentReference: DocumentReference): DocumentReference {
    return repository.create(documentReference)
  }

  suspend fun upsertDocumentReference(
    documentReference: DocumentReference
  ): UpsertResult<DocumentReference> {
    return repository.upsert(documentReference)
  }
}
