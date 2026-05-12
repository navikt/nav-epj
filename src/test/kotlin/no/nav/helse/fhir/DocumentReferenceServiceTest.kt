package no.nav.helse.fhir

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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.fhir.documentreference.DocumentReferenceRepository
import no.nav.helse.fhir.documentreference.DocumentReferenceService

class DocumentReferenceServiceTest {

  val documentReferenceRepository = mockk<DocumentReferenceRepository>()

  val documentReference1Id = "documentreference-001"
  val documentReference1 =
    DocumentReference(
      id = documentReference1Id,
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
                data = Base64Binary(value = "JVBERi0xLjQ="),
              )
          )
        ),
      context =
        DocumentReference.Context(
          encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-001")))
        ),
    )

  val documentReference2 =
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
                data = Base64Binary(value = "JVBERi0xLjQ="),
              )
          )
        ),
      context =
        DocumentReference.Context(
          encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-002")))
        ),
    )

  val documentReference3 =
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
                data = Base64Binary(value = "JVBERi0xLjQ="),
              )
          )
        ),
      context =
        DocumentReference.Context(
          encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-003")))
        ),
    )

  @Test
  fun `get document reference successfully and assert results`() = runBlocking {
    val documentReferenceService = DocumentReferenceService(documentReferenceRepository)
    coEvery { documentReferenceRepository.getById(any()) } returns documentReference1
    val documentReference = documentReferenceService.getDocumentReference(documentReference1Id)

    assertEquals(documentReference1.id, documentReference?.id)
    assertEquals(documentReference1.status, documentReference?.status)
    assertEquals(documentReference1.type, documentReference?.type)
    assertEquals(documentReference1.description, documentReference?.description)
    assertEquals(documentReference1.subject, documentReference?.subject)
    assertEquals(documentReference1.author, documentReference?.author)
    assertEquals(documentReference1.content, documentReference?.content)
    assertEquals(documentReference1.context, documentReference?.context)
  }

  @Test
  fun `get document reference with non existing id should return null`() = runBlocking {
    val documentReferenceService = DocumentReferenceService(documentReferenceRepository)
    coEvery { documentReferenceRepository.getById(any()) } returns null
    val documentReference = documentReferenceService.getDocumentReference("non-existing-id")

    assertEquals(null, documentReference)
  }

  @Test
  fun `get all document references should return all documents and assert that there are three`() =
    runBlocking {
      val documentReferenceService = DocumentReferenceService(documentReferenceRepository)
      coEvery { documentReferenceRepository.getAll() } returns
        listOf(documentReference1, documentReference2, documentReference3)
      val documentReferences = documentReferenceService.getAllDocumentReferences()

      assertEquals(3, documentReferences.size)
      assertTrue { documentReferences[0].id == documentReference1.id }
    }

  @Test
  fun `get document references returns an empty list when there are none`() = runBlocking {
    val documentReferenceService = DocumentReferenceService(documentReferenceRepository)
    coEvery { documentReferenceRepository.getAll() } returns emptyList()
    val documentReferences = documentReferenceService.getAllDocumentReferences()

    assertTrue { documentReferences.isEmpty() }
  }

  @Test
  fun `create document reference successfully`() = runBlocking {
    val documentReferenceService = DocumentReferenceService(documentReferenceRepository)
    val newDocumentReference =
      DocumentReference(
        id = "documentreference-new",
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
        description = FhirString(value = "100% Sykmelding fra 01.05.2024 til 15.05.2024"),
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
                  data = Base64Binary(value = "JVBERi0xLjQ="),
                )
            )
          ),
        context =
          DocumentReference.Context(
            encounter = listOf(Reference(reference = FhirString(value = "Encounter/encounter-001")))
          ),
      )
    coEvery { documentReferenceRepository.create(any()) } returns newDocumentReference

    val created = documentReferenceService.createDocumentReference(newDocumentReference)
    coVerify(exactly = 1) { documentReferenceRepository.create(newDocumentReference) }

    assertEquals(newDocumentReference.id, created.id)
    assertEquals(newDocumentReference.status, created.status)
    assertEquals(newDocumentReference.type, created.type)
    assertEquals(newDocumentReference.description, created.description)
    assertEquals(newDocumentReference.subject, created.subject)
    assertEquals(newDocumentReference.author, created.author)
    assertEquals(newDocumentReference.content, created.content)
    assertEquals(newDocumentReference.context, created.context)
  }
}
