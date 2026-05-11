package fhir.documentreference

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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.core.db.DatabaseConnection
import no.nav.helse.core.db.dbQuery
import no.nav.helse.fhir.documentreference.DocumentReferenceRepositoryImpl
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.testcontainers.containers.PostgreSQLContainer

class DocumentReferenceRepositoryImplTest {

  companion object {
    private val postgres =
      PostgreSQLContainer("postgres:17").apply {
        withDatabaseName("dr-zara-test")
        withUsername("postgres")
        withPassword("postgres")
        withInitScript("test-init.sql")
      }

    init {
      postgres.start()

      DatabaseConnection.database =
        R2dbcDatabase.Companion.connect(
          url =
            "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
          user = postgres.username,
          password = postgres.password,
        )
    }
  }

  private lateinit var repo: DocumentReferenceRepositoryImpl

  @BeforeTest
  fun setup() {
    repo = DocumentReferenceRepositoryImpl()

    runBlocking { dbQuery { exec("DELETE FROM document_reference") } }
  }

  private fun createTestDocumentReference(
    id: String = "documentreference-001",
    description: String = "100% Sykmelding",
    status: DocumentReferenceStatus = DocumentReferenceStatus.Current,
  ) =
    DocumentReference(
      id = id,
      status = Enumeration(value = status),
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
      description = FhirString(value = description),
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

  @Test
  fun `create document reference persists to database`() = runBlocking {
    val documentReference = createTestDocumentReference()

    val created = repo.create(documentReference)

    assertEquals(documentReference.id, created.id)
    assertEquals(documentReference.description, created.description)
  }

  @Test
  fun `get document reference returns persisted document reference`() = runBlocking {
    val documentReference = createTestDocumentReference()
    repo.create(documentReference)

    val retrieved = repo.getById(documentReference.id!!)

    assertNotNull(retrieved)
    assertEquals(documentReference.id, retrieved.id)
    assertEquals(documentReference.status.value, retrieved.status.value)
    assertEquals(documentReference.type, retrieved.type)
    assertEquals(documentReference.description, retrieved.description)
    assertEquals(documentReference.subject, retrieved.subject)
    assertEquals(documentReference.author, retrieved.author)
    assertEquals(documentReference.content, retrieved.content)
    assertEquals(documentReference.context, retrieved.context)
  }

  @Test
  fun `get document reference with non-existing id returns null`() = runBlocking {
    val retrieved = repo.getById("non-existing-id")

    assertNull(retrieved)
  }

  @Test
  fun `get all document references returns all persisted document references`() = runBlocking {
    val doc1 = createTestDocumentReference(id = "documentreference-001", description = "Doc 1")
    val doc2 = createTestDocumentReference(id = "documentreference-002", description = "Doc 2")
    val doc3 = createTestDocumentReference(id = "documentreference-003", description = "Doc 3")

    repo.create(doc1)
    repo.create(doc2)
    repo.create(doc3)

    val all = repo.getAll()

    assertEquals(3, all.size)
    assertTrue(all.any { it.id == "documentreference-001" })
    assertTrue(all.any { it.id == "documentreference-002" })
    assertTrue(all.any { it.id == "documentreference-003" })
  }

  @Test
  fun `get all document references returns empty list when no document references exist`() =
    runBlocking {
      val all = repo.getAll()

      assertTrue(all.isEmpty())
    }

  @Test
  fun `create document reference without id generates uuid`() = runBlocking {
    val documentReference =
      DocumentReference(
        id = null,
        status = Enumeration(value = DocumentReferenceStatus.Current),
        description = FhirString(value = "New document"),
        content =
          listOf(
            DocumentReference.Content(
              attachment =
                Attachment(
                  title = FhirString(value = "Document.pdf"),
                  contentType = Code(value = "application/pdf"),
                )
            )
          ),
      )

    val created = repo.create(documentReference)

    assertNotNull(created.id)
    assertTrue(created.id!!.startsWith("documentreference-"))
  }

  @Test
  fun `document reference with different statuses is stored and retrieved correctly`() =
    runBlocking {
      val currentDoc =
        createTestDocumentReference(id = "doc-current", status = DocumentReferenceStatus.Current)
      val supersededDoc =
        createTestDocumentReference(
          id = "doc-superseded",
          status = DocumentReferenceStatus.Superseded,
        )

      repo.create(currentDoc)
      repo.create(supersededDoc)

      val retrievedCurrent = repo.getById("doc-current")
      val retrievedSuperseded = repo.getById("doc-superseded")

      assertNotNull(retrievedCurrent)
      assertEquals(DocumentReferenceStatus.Current, retrievedCurrent.status?.value)

      assertNotNull(retrievedSuperseded)
      assertEquals(DocumentReferenceStatus.Superseded, retrievedSuperseded.status?.value)
    }

  @Test
  fun `document reference with attachment data is stored and retrieved correctly`() = runBlocking {
    val documentReference = createTestDocumentReference(id = "doc-with-attachment")

    repo.create(documentReference)
    val retrieved = repo.getById("doc-with-attachment")

    assertNotNull(retrieved)
    assertNotNull(retrieved.content)
    assertEquals(1, retrieved.content.size)
    assertEquals("Sykmelding.pdf", retrieved.content.first().attachment.title?.value)
    assertEquals("application/pdf", retrieved.content.first().attachment.contentType?.value)
  }

  @Test
  fun `attachment binary data survives database roundtrip and can be decoded`() = runBlocking {
    // Create a known PDF file header (minimal valid PDF)
    val pdfContent = "%PDF-1.4\n%Test PDF content for roundtrip verification\n%%EOF"
    val originalBytes = pdfContent.toByteArray(Charsets.UTF_8)
    val base64Encoded = java.util.Base64.getEncoder().encodeToString(originalBytes)

    val documentReference =
      DocumentReference(
        id = "doc-binary-roundtrip",
        status = Enumeration(value = DocumentReferenceStatus.Current),
        content =
          listOf(
            DocumentReference.Content(
              attachment =
                Attachment(
                  title = FhirString(value = "test-document.pdf"),
                  contentType = Code(value = "application/pdf"),
                  data = Base64Binary(value = base64Encoded),
                )
            )
          ),
      )

    repo.create(documentReference)
    val retrieved = repo.getById("doc-binary-roundtrip")

    assertNotNull(retrieved)
    val retrievedBase64 = retrieved.content.first().attachment.data?.value
    assertNotNull(retrievedBase64)

    // Decode the base64 and verify it matches the original bytes
    val decodedBytes = java.util.Base64.getDecoder().decode(retrievedBase64)
    assertTrue(originalBytes.contentEquals(decodedBytes))

    // Verify we can reconstruct the original content
    val reconstructedContent = String(decodedBytes, Charsets.UTF_8)
    assertEquals(pdfContent, reconstructedContent)
    assertTrue(reconstructedContent.startsWith("%PDF-1.4"))
  }
}
