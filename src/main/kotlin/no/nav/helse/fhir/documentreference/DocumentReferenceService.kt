package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import no.nav.helse.fhir.documentreference.repository.DocumentReferenceRepository

class DocumentReferenceService(private val repository: DocumentReferenceRepository) {

    fun getDocumentReference(id: String): DocumentReference? {
        return repository.getDocumentReference(id)
    }

    fun getAllDocumentReferences(): List<DocumentReference> {
        return repository.getAllDocumentReferences()
    }

    fun createDocumentReference(documentReference: DocumentReference): DocumentReference {
        return repository.createDocumentReference(documentReference)
    }
}
