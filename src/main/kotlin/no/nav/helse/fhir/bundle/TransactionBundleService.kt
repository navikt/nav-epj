package no.nav.helse.fhir.bundle

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.QuestionnaireResponse
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.Uri
import no.nav.helse.core.db.dbQuery
import no.nav.helse.fhir.documentreference.DocumentReferenceTable
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

sealed interface TransactionResult {
  data class Success(val responseBundle: Bundle) : TransactionResult

  data class Error(val message: String, val statusCode: Int = 400) : TransactionResult
}

private data class ResourceResult(val resource: Resource, val created: Boolean)

class TransactionBundleService {

  suspend fun processTransaction(bundle: Bundle): TransactionResult {
    if (bundle.type.value != Bundle.BundleType.Transaction) {
      return TransactionResult.Error("Bundle type must be 'transaction'", 400)
    }

    val entries = bundle.entry

    val documentReferences = mutableListOf<DocumentReference>()
    val questionnaireResponses = mutableListOf<QuestionnaireResponse>()

    for (entry in entries) {
      val resource = entry.resource ?: continue
      val request = entry.request ?: continue
      val method = request.method.value

      if (method != Bundle.HTTPVerb.Put) {
        return TransactionResult.Error(
          "Only PUT method is supported in transaction bundles, got: $method",
          400,
        )
      }

      when (resource) {
        is DocumentReference -> {
          if (resource.id == null) {
            return TransactionResult.Error("DocumentReference must have an id for PUT", 400)
          }
          documentReferences.add(resource)
        }
        is QuestionnaireResponse -> {
          if (resource.id == null) {
            return TransactionResult.Error("QuestionnaireResponse must have an id for PUT", 400)
          }
          questionnaireResponses.add(resource)
        }
        else -> {
          return TransactionResult.Error(
            "Unsupported resource type: ${resource.javaClass.simpleName}",
            400,
          )
        }
      }
    }

    val results: List<ResourceResult>
    try {
      results = dbQuery {
        val qrResults = questionnaireResponses.map { qr ->
          val exists =
            QuestionnaireResponseTable.selectAll()
              .where { QuestionnaireResponseTable.id eq qr.id!! }
              .count() > 0

          if (exists) {
            QuestionnaireResponseTable.update({ QuestionnaireResponseTable.id eq qr.id!! }) {
              it[QuestionnaireResponseTable.data] = qr
            }
            ResourceResult(qr, created = false)
          } else {
            QuestionnaireResponseTable.insert {
              it[QuestionnaireResponseTable.id] = qr.id!!
              it[QuestionnaireResponseTable.data] = qr
            }
            ResourceResult(qr, created = true)
          }
        }

        val drResults = documentReferences.map { dr ->
          val exists =
            DocumentReferenceTable.selectAll()
              .where { DocumentReferenceTable.id eq dr.id!! }
              .count() > 0

          if (exists) {
            DocumentReferenceTable.update({ DocumentReferenceTable.id eq dr.id!! }) {
              it[DocumentReferenceTable.data] = dr
            }
            ResourceResult(dr, created = false)
          } else {
            DocumentReferenceTable.insert {
              it[DocumentReferenceTable.id] = dr.id!!
              it[DocumentReferenceTable.data] = dr
            }
            ResourceResult(dr, created = true)
          }
        }

        qrResults + drResults
      }
    } catch (e: Exception) {
      return TransactionResult.Error("Transaction failed: ${e.message}", 500)
    }

    val responseEntries = results.map { result ->
      val resourceType =
        when (result.resource) {
          is QuestionnaireResponse -> "QuestionnaireResponse"
          is DocumentReference -> "DocumentReference"
          else -> "Resource"
        }
      val resourceId =
        when (val r = result.resource) {
          is QuestionnaireResponse -> r.id
          is DocumentReference -> r.id
          else -> null
        }
      val status = if (result.created) "201 Created" else "200 OK"

      Bundle.Entry(
        fullUrl = Uri(value = "$resourceType/$resourceId"),
        resource = result.resource,
        response =
          Bundle.Entry.Response(
            status = com.google.fhir.model.r4.String(value = status),
            location = Uri(value = "$resourceType/$resourceId"),
          ),
      )
    }

    return TransactionResult.Success(
      Bundle(
        type = Enumeration(value = Bundle.BundleType.Transaction_Response),
        entry = responseEntries,
      )
    )
  }
}
