package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import no.nav.helse.fhir.questionnaireresponse.UpsertResult

interface DocumentReferenceRepository {
  suspend fun getById(id: String): DocumentReference?

  suspend fun getAll(): List<DocumentReference>

  suspend fun create(documentReference: DocumentReference): DocumentReference

  suspend fun upsert(documentReference: DocumentReference): UpsertResult<DocumentReference>
}
