package no.nav.helse.fhir.documentreference.repository

import com.google.fhir.model.r4.DocumentReference

interface DocumentReferenceRepository {
    fun getDocumentReference(id: String): DocumentReference?

    fun getAllDocumentReferences(): List<DocumentReference>

    fun createDocumentReference(documentReference: DocumentReference): DocumentReference
}
