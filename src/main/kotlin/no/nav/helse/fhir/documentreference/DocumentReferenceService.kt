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
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.konsultasjon.Journalnotat
import no.nav.helse.epj.konsultasjon.JournalnotatId
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.pasient.PasientId

class DocumentReferenceService(
  val konsultasjonService: KonsultasjonService,
  val helsepersonellService: HelsepersonellService,
) {

  suspend fun createDocumentReference(documentReference: DocumentReference): Boolean {
    val createJournalnotat =
      Journalnotat(
        id =
          JournalnotatId(
            Uuid.parse(requireNotNull(documentReference.id) { "DocumentReference mangler id" })
          ),
        konsultasjonId =
          KonsultasjonId(
            Uuid.parse(
              requireNotNull(documentReference.context?.id) {
                "DocumentReference mangler context.id"
              }
            )
          ),
        pasientId =
          PasientId(
            Uuid.parse(
              requireNotNull(documentReference.subject?.id) {
                "DocumentReference mangler subject.id"
              }
            )
          ),
        journalnotat =
          requireNotNull(documentReference.description.toString()) {
            "DocumentReference mangler description"
          },
      )
    return konsultasjonService.createJournalnotat(createJournalnotat)
  }

  suspend fun getDocumentReferences(documentReferenceId: DocumentReferenceId): DocumentReference? {
    val journalnotat =
      konsultasjonService.getJournalnotat(JournalnotatId(documentReferenceId.value)) ?: return null
    val hpr = helsepersonellService.getHelsepersonell(journalnotat.pasientId)
    return journalnotat.toDocumentReference(hpr)
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
