package no.nav.helse.fhir.documentreference.repository

import com.google.fhir.model.r4.DocumentReference
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

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
}
