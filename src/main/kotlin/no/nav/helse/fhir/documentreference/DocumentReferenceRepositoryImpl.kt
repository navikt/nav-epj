package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.DocumentReference
import java.util.UUID
import no.nav.helse.core.db.dbQuery
import no.nav.helse.fhir.questionnaireresponse.UpsertResult
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class DocumentReferenceRepositoryImpl : DocumentReferenceRepository {

  override suspend fun getById(id: String): DocumentReference? {
    return dbQuery {
      DocumentReferenceTable.selectAll()
        .where { DocumentReferenceTable.id eq id }
        .singleOrNull()
        ?.let { it[DocumentReferenceTable.data] }
    }
  }

  override suspend fun getAll(): List<DocumentReference> {
    return dbQuery {
      DocumentReferenceTable.selectAll().map { it[DocumentReferenceTable.data] }.toList()
    }
  }

  override suspend fun create(documentReference: DocumentReference): DocumentReference {
    val id = documentReference.id ?: "documentreference-${UUID.randomUUID()}"
    val documentReferenceData =
      if (documentReference.id == null) documentReference.copy(id = id) else documentReference

    dbQuery {
      DocumentReferenceTable.insert {
        it[DocumentReferenceTable.id] = id
        it[DocumentReferenceTable.data] = documentReferenceData
      }
    }

    return documentReferenceData
  }

  override suspend fun upsert(
    documentReference: DocumentReference
  ): UpsertResult<DocumentReference> {
    val id =
      documentReference.id
        ?: throw IllegalArgumentException("DocumentReference must have an id for upsert")

    val created = dbQuery {
      val exists =
        DocumentReferenceTable.selectAll().where { DocumentReferenceTable.id eq id }.count() > 0

      if (exists) {
        DocumentReferenceTable.update({ DocumentReferenceTable.id eq id }) {
          it[DocumentReferenceTable.data] = documentReference
        }
        false
      } else {
        DocumentReferenceTable.insert {
          it[DocumentReferenceTable.id] = id
          it[DocumentReferenceTable.data] = documentReference
        }
        true
      }
    }

    return UpsertResult(documentReference, created)
  }
}
