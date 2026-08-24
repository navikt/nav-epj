package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Attachment
import com.google.fhir.model.r4.Base64Binary
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.CommonLanguages
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.Journalnotat
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

class DocumentReferenceService(private val epjClient: HttpClient) {

  suspend fun createDocumentReference(documentReference: DocumentReference): Boolean {
    val createJournalnotat =
      CreateJournalnotat(
        id = documentReference.id?.let { DocumentReferenceId(Uuid.parse(it)) },
        konsultasjonId = documentReference.context?.id?.let { EncounterId(Uuid.parse(it)) },
        pasientId = documentReference.subject?.id?.let { PatientInputId(Uuid.parse(it)) },
        journalnotat = documentReference.description.toString(),
      )
    val response =
      epjClient.post("/api/journalnotat") {
        contentType(ContentType.Application.Json)
        setBody(createJournalnotat)
      }
    return response.status == HttpStatusCode.Created
  }

  suspend fun getDocumentReferences(documentReferenceId: DocumentReferenceId): DocumentReference? {
    val response = epjClient.get("/api/journalnotat/${documentReferenceId.value}")
    if (response.status != HttpStatusCode.OK) return null
    val journalNotat = response.body<Journalnotat>()

    val responseHelsepersonell =
      epjClient.get("/api/helsepersonell/patient/${journalNotat.pasientId}")

    if (responseHelsepersonell.status != HttpStatusCode.OK) return null
    val hpr = responseHelsepersonell.body<List<HelsepersonellHpr>>()
    return journalNotat.toDocumentReference(hpr)
  }

  fun Journalnotat.toDocumentReference(hpr: List<HelsepersonellHpr>): DocumentReference {
    return DocumentReference(
      id = this.id.value.toString(),
      description = String(value = this.journalnotat),
      type =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.9602"),
                code = Code(value = "J01-2"),
                display = String(value = "Sykmeldinger og trygdesaker"),
              )
            )
        ),
      content =
        listOf(
          DocumentReference.Content(
            attachment =
              Attachment(
                title =
                  String(
                    value = "tittel generert av Nav"
                  ), // TODO: denne skal vel ikke være hardkodet?
                language = Enumeration(value = CommonLanguages.No_No),
                contentType = Code(value = "application/pdf"),
                data = Base64Binary(value = "base64 PDF"),
              )
          )
        ),
      subject = Reference(reference = String(value = "Patient/${this.pasientId.value}")),
      author = hpr.map { Reference(reference = String(value = "Practitioner/${it}")) },
      context =
        DocumentReference.Context(
          encounter =
            listOf(Reference(reference = String(value = "Encounter/${this.konsultasjonId.value}")))
        ),
      status = Enumeration(value = DocumentReferenceStatus.Current),
    )
  }
}
