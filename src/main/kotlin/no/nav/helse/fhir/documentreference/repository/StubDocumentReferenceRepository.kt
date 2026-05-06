package no.nav.helse.fhir.documentreference.repository

import com.google.fhir.model.r4.Attachment
import com.google.fhir.model.r4.Base64Binary
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.CommonLanguages
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus

class StubDocumentReferenceRepository : DocumentReferenceRepository {

  private val documentReferences =
    mutableListOf(
      DocumentReference(
        id = "documentreference-001",
        status = Enumeration(value = DocumentReferenceStatus.Current),
        type =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.9602"),
                  code = Code(value = "J01-2"),
                  display = FhirString(value = "Sykmeldinger og trygdesaker"),
                )
              )
          ),
        description = FhirString(value = "100% Sykmelding fra 01.06.2024 til 07.06.2024"),
        subject = Reference(reference = FhirString(value = "Patient/patient-001")),
        author = listOf(Reference(reference = FhirString(value = "Practitioner/practitioner-001"))),
        content =
          listOf(
            DocumentReference.Content(
              attachment =
                Attachment(
                  title = FhirString(value = "Sykmelding.pdf"),
                  language = Enumeration(value = CommonLanguages.No_No),
                  contentType = Code(value = "application/pdf"),
                  data =
                    Base64Binary(
                      value =
                        "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK"
                    ),
                )
            )
          ),
        context =
          DocumentReference.Context(
            encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-001")))
          ),
      ),
      DocumentReference(
        id = "documentreference-002",
        status = Enumeration(value = DocumentReferenceStatus.Current),
        type =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.9602"),
                  code = Code(value = "J01-2"),
                  display = FhirString(value = "Sykmeldinger og trygdesaker"),
                )
              )
          ),
        description = FhirString(value = "50% Sykmelding fra 10.03.2024 til 24.03.2024"),
        subject = Reference(reference = FhirString(value = "Patient/patient-002")),
        author = listOf(Reference(reference = FhirString(value = "Practitioner/practitioner-002"))),
        content =
          listOf(
            DocumentReference.Content(
              attachment =
                Attachment(
                  title = FhirString(value = "Sykmelding.pdf"),
                  language = Enumeration(value = CommonLanguages.No_No),
                  contentType = Code(value = "application/pdf"),
                  data =
                    Base64Binary(
                      value =
                        "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK"
                    ),
                )
            )
          ),
        context =
          DocumentReference.Context(
            encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-002")))
          ),
      ),
      DocumentReference(
        id = "documentreference-003",
        status = Enumeration(value = DocumentReferenceStatus.Current),
        type =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "urn:oid:2.16.578.1.12.4.1.1.9602"),
                  code = Code(value = "J01-2"),
                  display = FhirString(value = "Sykmeldinger og trygdesaker"),
                )
              )
          ),
        description = FhirString(value = "100% Sykmelding fra 20.04.2024 til 30.04.2024"),
        subject = Reference(reference = FhirString(value = "Patient/patient-003")),
        author = listOf(Reference(reference = FhirString(value = "Practitioner/practitioner-001"))),
        content =
          listOf(
            DocumentReference.Content(
              attachment =
                Attachment(
                  title = FhirString(value = "Sykmelding.pdf"),
                  language = Enumeration(value = CommonLanguages.No_No),
                  contentType = Code(value = "application/pdf"),
                  data =
                    Base64Binary(
                      value =
                        "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK"
                    ),
                )
            )
          ),
        context =
          DocumentReference.Context(
            encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-003")))
          ),
      ),
    )

  override fun getDocumentReference(id: String): DocumentReference? {
    return documentReferences.find { it.id == id }
  }

  override fun getAllDocumentReferences(): List<DocumentReference> {
    return documentReferences
  }

  override fun createDocumentReference(documentReference: DocumentReference): DocumentReference {
    val newDocumentReference =
      if (documentReference.id == null) {
        documentReference.copy(id = "documentreference-${java.util.UUID.randomUUID()}")
      } else {
        documentReference
      }
    documentReferences.add(newDocumentReference)
    return newDocumentReference
  }
}
