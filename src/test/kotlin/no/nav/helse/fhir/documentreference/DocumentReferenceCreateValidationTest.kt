@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Attachment
import com.google.fhir.model.r4.Base64Binary
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Extension
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Test

private const val TYPE_SYSTEM = "urn:oid:2.16.578.1.12.4.1.1.9602"
private const val TYPE_CODE = "J01-2"

class DocumentReferenceCreateValidationTest {

    private fun validTypeCoding() =
        listOf(
            Coding(
                system = Uri(value = TYPE_SYSTEM),
                code = Code(value = TYPE_CODE),
                display = FhirString(value = "Sykmeldinger og trygdesaker"),
            )
        )

    private fun validContent(
        attachment: Attachment = Attachment(contentType = Code(value = "application/pdf"))
    ) = listOf(DocumentReference.Content(attachment = attachment))

    private fun validDocumentReference(
        id: String? = null,
        status: DocumentReferenceStatus? = DocumentReferenceStatus.Current,
        type: CodeableConcept? = CodeableConcept(coding = validTypeCoding()),
        subjectReference: String? = "Patient/${Uuid.generateV4()}",
        context: DocumentReference.Context? =
            DocumentReference.Context(
                encounter =
                    listOf(
                        Reference(reference = FhirString(value = "Encounter/${Uuid.generateV4()}"))
                    )
            ),
        description: String? = "Pasienten har det bra",
        content: List<DocumentReference.Content> = validContent(),
        identifier: List<Identifier> = emptyList(),
        author: List<Reference> = emptyList(),
    ): DocumentReference =
        DocumentReference(
            id = id,
            status = Enumeration(value = status),
            type = type,
            subject = subjectReference?.let { Reference(reference = FhirString(value = it)) },
            context = context,
            description = description?.let { FhirString(value = it) },
            content = content,
            identifier = identifier,
            author = author,
        )

    @Test
    fun `toOpprettJournalnotatRequest maps a fully valid DocumentReference`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        val documentReference =
            validDocumentReference(
                subjectReference = "Patient/$patientId",
                context =
                    DocumentReference.Context(
                        encounter =
                            listOf(
                                Reference(reference = FhirString(value = "Encounter/$encounterId"))
                            )
                    ),
                description = "Journalnotat tekst",
            )

        val request = documentReference.toOpprettJournalnotatRequest()

        assertEquals(patientId, request.pasientId.value)
        assertEquals(encounterId, request.konsultasjonId.value)
        assertEquals("Journalnotat tekst", request.journalnotat)
    }

    @Test
    fun `toOpprettJournalnotatRequest ignores a client-supplied id`() {
        val documentReference = validDocumentReference(id = "client-chosen-id")

        documentReference.toOpprettJournalnotatRequest()
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing status`() {
        val documentReference = validDocumentReference(status = null)

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects an unsupported status value`() {
        val documentReference = validDocumentReference(status = DocumentReferenceStatus.Superseded)

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing type`() {
        val documentReference = validDocumentReference(type = null)

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Required, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects zero type codings`() {
        val documentReference = validDocumentReference(type = CodeableConcept(coding = emptyList()))

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects multiple type codings`() {
        val documentReference =
            validDocumentReference(
                type = CodeableConcept(coding = validTypeCoding() + validTypeCoding())
            )

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects an unsupported type system or code`() {
        val documentReference =
            validDocumentReference(
                type =
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system = Uri(value = "http://snomed.info/sct"),
                                    code = Code(value = "1234"),
                                )
                            )
                    )
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing subject`() {
        val documentReference = validDocumentReference(subjectReference = null)

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a subject reference to the wrong resource type`() {
        val documentReference =
            validDocumentReference(subjectReference = "Group/${Uuid.generateV4()}")

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a subject reference with a malformed UUID`() {
        val documentReference = validDocumentReference(subjectReference = "Patient/not-a-uuid")

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing context`() {
        val documentReference = validDocumentReference(context = null)

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Required, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing encounter`() {
        val documentReference =
            validDocumentReference(context = DocumentReference.Context(encounter = emptyList()))

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects more than one encounter`() {
        val documentReference =
            validDocumentReference(
                context =
                    DocumentReference.Context(
                        encounter =
                            listOf(
                                Reference(
                                    reference = FhirString(value = "Encounter/${Uuid.generateV4()}")
                                ),
                                Reference(
                                    reference = FhirString(value = "Encounter/${Uuid.generateV4()}")
                                ),
                            )
                    )
            )

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects an encounter reference with a malformed UUID`() {
        val documentReference =
            validDocumentReference(
                context =
                    DocumentReference.Context(
                        encounter =
                            listOf(
                                Reference(reference = FhirString(value = "Encounter/not-a-uuid"))
                            )
                    )
            )

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects an unsupported context field`() {
        val documentReference =
            validDocumentReference(
                context =
                    DocumentReference.Context(
                        encounter =
                            listOf(
                                Reference(
                                    reference = FhirString(value = "Encounter/${Uuid.generateV4()}")
                                )
                            ),
                        facilityType = CodeableConcept(text = FhirString(value = "legekontor")),
                    )
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing description`() {
        val documentReference = validDocumentReference(description = null)

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Required, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a blank description`() {
        val documentReference = validDocumentReference(description = "   ")

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects zero content entries`() {
        val documentReference = validDocumentReference(content = emptyList())

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects more than one content entry`() {
        val documentReference = validDocumentReference(content = validContent() + validContent())

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a content format`() {
        val documentReference =
            validDocumentReference(
                content =
                    listOf(
                        DocumentReference.Content(
                            attachment = Attachment(contentType = Code(value = "application/pdf")),
                            format = Coding(code = Code(value = "urn:ihe:pcc:xphr:2007")),
                        )
                    )
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    private fun extension() =
        Extension(
            url = "http://example.org/ext",
            value = Extension.Value.String(FhirString(value = "x")),
        )

    private fun assertNotSupported(documentReference: DocumentReference) {
        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a content extension`() {
        assertNotSupported(
            validDocumentReference(
                content =
                    listOf(
                        DocumentReference.Content(
                            attachment = Attachment(contentType = Code(value = "application/pdf")),
                            extension = listOf(extension()),
                        )
                    )
            )
        )
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a content modifierExtension`() {
        assertNotSupported(
            validDocumentReference(
                content =
                    listOf(
                        DocumentReference.Content(
                            attachment = Attachment(contentType = Code(value = "application/pdf")),
                            modifierExtension = listOf(extension()),
                        )
                    )
            )
        )
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects an attachment extension`() {
        assertNotSupported(
            validDocumentReference(
                content =
                    validContent(
                        Attachment(
                            contentType = Code(value = "application/pdf"),
                            extension = listOf(extension()),
                        )
                    )
            )
        )
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a missing attachment contentType`() {
        val documentReference = validDocumentReference(content = validContent(Attachment()))

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Value, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects an unsupported attachment contentType`() {
        val documentReference =
            validDocumentReference(
                content = validContent(Attachment(contentType = Code(value = "text/plain")))
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Value, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects client-supplied attachment data instead of discarding it silently`() {
        val documentReference =
            validDocumentReference(
                content =
                    validContent(
                        Attachment(
                            contentType = Code(value = "application/pdf"),
                            data = Base64Binary(value = "cXVpdGUgcmVhbCBjb250ZW50"),
                        )
                    )
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a client-supplied attachment title`() {
        val documentReference =
            validDocumentReference(
                content =
                    validContent(
                        Attachment(
                            contentType = Code(value = "application/pdf"),
                            title = FhirString(value = "Mitt eget notat"),
                        )
                    )
            )

        assertFailsWith<InvalidDocumentReferenceException> {
            documentReference.toOpprettJournalnotatRequest()
        }
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects identifiers`() {
        val documentReference =
            validDocumentReference(
                identifier = listOf(Identifier(value = FhirString(value = "123")))
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toOpprettJournalnotatRequest rejects a client-supplied author`() {
        val documentReference =
            validDocumentReference(
                author = listOf(Reference(reference = FhirString(value = "Practitioner/999")))
            )

        val exception =
            assertFailsWith<InvalidDocumentReferenceException> {
                documentReference.toOpprettJournalnotatRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }
}
