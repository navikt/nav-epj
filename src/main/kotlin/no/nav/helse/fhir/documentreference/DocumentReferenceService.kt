package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Attachment
import com.google.fhir.model.r4.Base64Binary
import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.CommonLanguages
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import java.util.Base64
import kotlin.text.substringAfter
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.ehr.consultation.ClinicalNote
import no.nav.helse.ehr.consultation.ClinicalNoteAttachment
import no.nav.helse.ehr.consultation.ClinicalNoteId
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.consultation.CreateClinicalNoteRequest
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

private const val CLINICAL_NOTE_TYPE_SYSTEM = "urn:oid:2.16.578.1.12.4.1.1.9602"
private const val CLINICAL_NOTE_TYPE_CODE = "J01-2"
private const val CLINICAL_NOTE_TYPE_DISPLAY = "Sykmeldinger og trygdesaker"
private const val CLINICAL_NOTE_CONTENT_TYPE = "application/pdf"
private const val MAX_ATTACHMENT_MEGABYTES = 10
private const val MAX_ATTACHMENT_BYTES = MAX_ATTACHMENT_MEGABYTES * 1_000_000
private const val DEFAULT_ATTACHMENT_TITLE = "dokument.pdf"
private val PDF_SIGNATURE = "%PDF-".toByteArray()

/**
 * Raised when an incoming DocumentReference does not fit the narrow journal-note shape backed by
 * [ClinicalNote]. Carries the FHIR [OperationOutcome.IssueType] so the route can turn it into a
 * matching OperationOutcome without re-deriving the classification.
 */
class InvalidDocumentReferenceException(
    val issueType: OperationOutcome.IssueType,
    message: kotlin.String,
) : RuntimeException(message)

class DocumentReferenceService(val consultationService: ConsultationService) {

    val log = logger()

    /** [orgNumber] is null for a system token, which is not tied to one clinic. */
    suspend fun createDocumentReference(
        request: CreateClinicalNoteRequest,
        orgNumber: kotlin.String?,
    ): DocumentReference {
        val clinicalNote = consultationService.createClinicalNote(request, orgNumber)
        return clinicalNote.toDocumentReference(
            authors(clinicalNote.consultationId, orgNumber),
            request.attachment,
        )
    }

    private suspend fun ClinicalNote.attachment(
        orgNumber: kotlin.String?
    ): ClinicalNoteAttachment? =
        if (hasAttachment) consultationService.getClinicalNoteAttachment(id, orgNumber) else null

    private suspend fun authors(
        consultationId: ConsultationId,
        orgNumber: kotlin.String?,
    ): List<PractitionerHpr> =
        consultationService.getConsultation(consultationId, orgNumber).hpr.map {
            PractitionerHpr(it)
        }

    suspend fun searchDocumentReferences(
        patientId: PatientInputId,
        encounterId: EncounterId?,
        orgNumber: kotlin.String?,
    ): Bundle {
        val clinicalNotes =
            consultationService.getClinicalNotes(
                PatientId(patientId.value),
                encounterId?.let { ConsultationId(it.value) },
                orgNumber,
            )
        val hprByConsultation =
            consultationService.getConsultations(PatientId(patientId.value), orgNumber).associate {
                it.id to it.hpr.map { hpr -> PractitionerHpr(hpr) }
            }

        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                clinicalNotes.map { clinicalNote ->
                    val documentReference =
                        clinicalNote.toDocumentReference(
                            hprByConsultation[clinicalNote.consultationId].orEmpty(),
                            clinicalNote.attachment(orgNumber),
                        )
                    Bundle.Entry(
                        fullUrl = Uri(value = "DocumentReference/${documentReference.id}"),
                        resource = documentReference,
                    )
                },
        )
    }

    suspend fun createDocumentReference(
        documentReference: DocumentReference,
        orgNumber: kotlin.String,
    ): Boolean {
        val consultationId =
            documentReference.context
                ?.encounter
                ?.first()
                ?.reference
                ?.value
                ?.substringAfter("Encounter/")
        val patientId = documentReference.subject?.reference?.value?.substringAfter("Patient/")

        val clinicalNote =
            ClinicalNote(
                id =
                    ClinicalNoteId(
                        Uuid.parse(
                            requireNotNull(documentReference.id) { "DocumentReference mangler id" }
                        )
                    ),
                consultationId =
                    ConsultationId(
                        Uuid.parse(
                            requireNotNull(consultationId) {
                                "DocumentReference mangler context.encounter.reference"
                            }
                        )
                    ),
                patientId =
                    PatientId(
                        Uuid.parse(
                            requireNotNull(patientId) {
                                "DocumentReference mangler subject.reference"
                            }
                        )
                    ),
                clinicalNote =
                    requireNotNull(documentReference.description?.value) {
                        "DocumentReference mangler description"
                    },
            )

        return consultationService.insertClinicalNote(clinicalNote, orgNumber)
    }

    suspend fun getDocumentReferences(
        documentReferenceId: DocumentReferenceId,
        orgNumber: kotlin.String?,
    ): DocumentReference? {
        val clinicalNote =
            consultationService.getClinicalNote(
                ClinicalNoteId(documentReferenceId.value),
                orgNumber,
            ) ?: return null
        return clinicalNote.toDocumentReference(
            authors(clinicalNote.consultationId, orgNumber),
            clinicalNote.attachment(orgNumber),
        )
    }

    /**
     * A note with a stored document returns it with its title. A note without one keeps the
     * placeholder attachment it has always had, so existing clients see no change.
     */
    fun ClinicalNote.toDocumentReference(
        hpr: List<PractitionerHpr>,
        attachment: ClinicalNoteAttachment? = null,
    ): DocumentReference {
        return DocumentReference(
            id = this.id.value.toString(),
            description = String(value = this.clinicalNote),
            type =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = CLINICAL_NOTE_TYPE_SYSTEM),
                                code = Code(value = CLINICAL_NOTE_TYPE_CODE),
                                display = String(value = CLINICAL_NOTE_TYPE_DISPLAY),
                            )
                        )
                ),
            content =
                listOf(
                    DocumentReference.Content(
                        attachment =
                            Attachment(
                                title =
                                    String(value = attachment?.title ?: "tittel generert av Nav"),
                                language = Enumeration(value = CommonLanguages.No_No),
                                contentType = Code(value = CLINICAL_NOTE_CONTENT_TYPE),
                                data =
                                    Base64Binary(
                                        value =
                                            attachment?.let {
                                                Base64.getEncoder().encodeToString(it.data)
                                            } ?: "base64 PDF"
                                    ),
                            )
                    )
                ),
            subject = Reference(reference = String(value = "Patient/${this.patientId.value}")),
            author = hpr.map { Reference(reference = String(value = "Practitioner/${it.value}")) },
            context =
                DocumentReference.Context(
                    encounter =
                        listOf(
                            Reference(
                                reference = String(value = "Encounter/${this.consultationId.value}")
                            )
                        )
                ),
            status = Enumeration(value = DocumentReferenceStatus.Current),
        )
    }
}

private fun invalid(issueType: OperationOutcome.IssueType, message: kotlin.String): Nothing =
    throw InvalidDocumentReferenceException(issueType, message)

/**
 * Validates and maps an incoming create request to the narrow journal-note shape backed by
 * [ClinicalNote]. The client-supplied `id`, if any, is intentionally never read here: create always
 * assigns a server-generated id, per FHIR create semantics. `status`, `type` and `content` are not
 * persisted; they are always regenerated on read, so they are validated against the only shape the
 * server can honestly reproduce rather than silently discarded.
 */
fun DocumentReference.toCreateClinicalNoteRequest(): CreateClinicalNoteRequest {
    rejectUnsupportedFields(this)

    if (this.status.value != DocumentReferenceStatus.Current) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.status støtter kun verdien 'current'",
        )
    }

    val type =
        this.type
            ?: invalid(OperationOutcome.IssueType.Required, "DocumentReference.type er påkrevd")
    val typeCodings = type.coding
    if (typeCodings.size != 1) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "DocumentReference.type.coding må inneholde nøyaktig én coding",
        )
    }
    val typeCoding = typeCodings.single()
    if (
        typeCoding.system?.value != CLINICAL_NOTE_TYPE_SYSTEM ||
            typeCoding.code?.value != CLINICAL_NOTE_TYPE_CODE
    ) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.type støtter kun system '$CLINICAL_NOTE_TYPE_SYSTEM' " +
                "og kode '$CLINICAL_NOTE_TYPE_CODE'",
        )
    }

    if (this.content.size != 1) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "DocumentReference.content må inneholde nøyaktig én oppføring",
        )
    }
    val content = this.content.single()
    val unsupportedContentFields = buildList {
        if (content.format != null) add("format")
        if (content.extension.isNotEmpty()) add("extension")
        if (content.modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    if (unsupportedContentFields.isNotEmpty()) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.content.${unsupportedContentFields.joinToString(", ")} støttes ikke",
        )
    }
    val attachment = content.attachment
    if (attachment.contentType?.value != CLINICAL_NOTE_CONTENT_TYPE) {
        invalid(
            OperationOutcome.IssueType.Value,
            "DocumentReference.content.attachment.contentType må være " +
                "'$CLINICAL_NOTE_CONTENT_TYPE'",
        )
    }
    val unsupportedAttachmentFields = buildList {
        if (attachment.language != null) add("language")
        if (attachment.url != null) add("url")
        if (attachment.size != null) add("size")
        if (attachment.hash != null) add("hash")
        if (attachment.creation != null) add("creation")
        if (attachment.extension.isNotEmpty()) add("extension")
    }
    if (unsupportedAttachmentFields.isNotEmpty()) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.content.attachment.${unsupportedAttachmentFields.joinToString(", ")} " +
                "støttes ikke",
        )
    }

    val documentAttachment = attachment.toClinicalNoteAttachment()

    val patientId = parseReferenceId(this.subject?.reference?.value, "Patient", "subject")

    val context =
        this.context
            ?: invalid(OperationOutcome.IssueType.Required, "DocumentReference.context er påkrevd")
    val unsupportedContextFields = buildList {
        if (context.event.isNotEmpty()) add("event")
        if (context.period != null) add("period")
        if (context.facilityType != null) add("facilityType")
        if (context.practiceSetting != null) add("practiceSetting")
        if (context.sourcePatientInfo != null) add("sourcePatientInfo")
        if (context.related.isNotEmpty()) add("related")
        if (context.extension.isNotEmpty()) add("extension")
        if (context.modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    if (unsupportedContextFields.isNotEmpty()) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.context.${unsupportedContextFields.joinToString(", ")} støttes ikke",
        )
    }
    if (context.encounter.size != 1) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "DocumentReference.context.encounter må inneholde nøyaktig én referanse",
        )
    }
    val consultationId =
        parseReferenceId(
            context.encounter.single().reference?.value,
            "Encounter",
            "context.encounter",
        )

    val clinicalNote =
        this.description?.value?.takeIf { it.isNotBlank() }
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "DocumentReference.description er påkrevd",
            )

    return CreateClinicalNoteRequest(
        patientId = PatientId(patientId),
        consultationId = ConsultationId(consultationId),
        clinicalNote = clinicalNote,
        attachment = documentAttachment,
    )
}

/**
 * The PDF a client sends in `attachment.data`, or null for a note without a document. The data must
 * be base64 and start like a PDF, and `title` is only accepted together with data.
 */
private fun Attachment.toClinicalNoteAttachment(): ClinicalNoteAttachment? {
    val base64 = this.data?.value
    if (base64 == null) {
        if (this.title != null) {
            invalid(
                OperationOutcome.IssueType.Invalid,
                "DocumentReference.content.attachment.title krever attachment.data",
            )
        }
        return null
    }
    val bytes =
        try {
            Base64.getDecoder().decode(base64)
        } catch (exception: IllegalArgumentException) {
            invalid(
                OperationOutcome.IssueType.Value,
                "DocumentReference.content.attachment.data er ikke gyldig base64: ${exception.message}",
            )
        }
    if (bytes.size > MAX_ATTACHMENT_BYTES) {
        invalid(
            OperationOutcome.IssueType.Too_Long,
            "DocumentReference.content.attachment.data er større enn $MAX_ATTACHMENT_MEGABYTES MB",
        )
    }
    if (!bytes.startsWith(PDF_SIGNATURE)) {
        invalid(
            OperationOutcome.IssueType.Value,
            "DocumentReference.content.attachment.data må være en PDF",
        )
    }
    val title = this.title?.value?.takeIf { it.isNotBlank() } ?: DEFAULT_ATTACHMENT_TITLE
    return ClinicalNoteAttachment(title, bytes)
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun parseReferenceId(
    reference: kotlin.String?,
    resourceType: kotlin.String,
    field: kotlin.String,
): Uuid {
    val prefix = "$resourceType/"
    if (reference == null) {
        invalid(OperationOutcome.IssueType.Required, "DocumentReference.$field er påkrevd")
    }
    if (!reference.startsWith(prefix)) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "DocumentReference.$field.reference må starte med '$prefix'",
        )
    }
    return try {
        Uuid.parse(reference.removePrefix(prefix))
    } catch (exception: IllegalArgumentException) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "DocumentReference.$field.reference er ikke en gyldig UUID: ${exception.message}",
        )
    }
}

/**
 * Rejects every DocumentReference element that [ClinicalNote] cannot represent, so unsupported
 * content is never silently discarded on create.
 */
private fun rejectUnsupportedFields(documentReference: DocumentReference) {
    val unsupported = buildList {
        if (documentReference.masterIdentifier != null) add("masterIdentifier")
        if (documentReference.identifier.isNotEmpty()) add("identifier")
        if (documentReference.docStatus != null) add("docStatus")
        if (documentReference.category.isNotEmpty()) add("category")
        if (documentReference.date != null) add("date")
        if (documentReference.author.isNotEmpty()) add("author")
        if (documentReference.authenticator != null) add("authenticator")
        if (documentReference.custodian != null) add("custodian")
        if (documentReference.relatesTo.isNotEmpty()) add("relatesTo")
        if (documentReference.securityLabel.isNotEmpty()) add("securityLabel")
        if (documentReference.extension.isNotEmpty()) add("extension")
        if (documentReference.modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    if (unsupported.isNotEmpty()) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.${unsupported.joinToString(", ")} støttes ikke",
        )
    }
}
