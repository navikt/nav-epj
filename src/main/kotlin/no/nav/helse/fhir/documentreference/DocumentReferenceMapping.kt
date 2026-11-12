package no.nav.helse.fhir.documentreference

import com.google.fhir.model.r4.Attachment
import com.google.fhir.model.r4.Base64Binary
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.DocumentReference
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Period
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.CommonLanguages
import com.google.fhir.model.r4.terminologies.DocumentReferenceStatus
import io.ktor.http.*
import java.time.LocalDate
import java.util.Base64
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.nav.helse.ehr.consultation.ClinicalDocument
import no.nav.helse.ehr.consultation.ClinicalDocumentWrite
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.NewAttachment
import no.nav.helse.ehr.consultation.PDF_CONTENT_TYPE
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.fhirFailure
import no.nav.helse.fhir.invalidResource
import no.nav.helse.fhir.parsePractitionerHpr
import no.nav.helse.fhir.parseReferenceId

const val MAX_ATTACHMENT_BYTES = 2_000_000
const val MAX_ATTACHMENT_BASE64_CHARS = (MAX_ATTACHMENT_BYTES + 2) / 3 * 4
private const val MAX_TEXT_LENGTH = 255
private const val CLINICAL_NOTE_TYPE_SYSTEM = "urn:oid:2.16.578.1.12.4.1.1.9602"
private const val CLINICAL_NOTE_TYPE_CODE = "J01-2"
private const val CLINICAL_NOTE_TYPE_DISPLAY = "Sykmeldinger og trygdesaker"
private const val OWNER = "DocumentReference"
private val PDF_SIGNATURE = "%PDF-".toByteArray()

fun DocumentReference.toClinicalDocumentWrite(): ClinicalDocumentWrite {
    rejectUnsupportedFields()

    if (status.value != DocumentReferenceStatus.Current) {
        invalidResource(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.status støtter kun verdien 'current'",
        )
    }

    val type =
        type ?: invalidResource(OperationOutcome.IssueType.Required, "$OWNER.type er påkrevd")
    val typeCoding =
        type.coding.singleOrNull()
            ?: invalidResource(
                OperationOutcome.IssueType.Invalid,
                "$OWNER.type.coding må inneholde nøyaktig én coding",
            )
    if (
        typeCoding.system?.value != CLINICAL_NOTE_TYPE_SYSTEM ||
            typeCoding.code?.value != CLINICAL_NOTE_TYPE_CODE
    ) {
        invalidResource(
            OperationOutcome.IssueType.Not_Supported,
            "$OWNER.type støtter kun system '$CLINICAL_NOTE_TYPE_SYSTEM' " +
                "og kode '$CLINICAL_NOTE_TYPE_CODE'",
        )
    }

    val content =
        content.singleOrNull()
            ?: invalidResource(
                OperationOutcome.IssueType.Invalid,
                "$OWNER.content må inneholde nøyaktig én oppføring",
            )
    content.rejectUnsupportedFields()

    val context =
        context ?: invalidResource(OperationOutcome.IssueType.Required, "$OWNER.context er påkrevd")
    context.rejectUnsupportedFields()
    val encounter =
        context.encounter.singleOrNull()
            ?: invalidResource(
                OperationOutcome.IssueType.Invalid,
                "$OWNER.context.encounter må inneholde nøyaktig én referanse",
            )

    val periodStart = context.period?.start.toLocalDate("context.period.start")
    val periodEnd = context.period?.end.toLocalDate("context.period.end")
    if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
        invalidResource(
            OperationOutcome.IssueType.Invalid,
            "$OWNER.context.period.end kan ikke være før start",
        )
    }

    return ClinicalDocumentWrite(
        patientId =
            PatientId(parseReferenceId(subject?.reference?.value, "Patient", OWNER, "subject")),
        consultationId =
            ConsultationId(
                parseReferenceId(
                    encounter.reference?.value,
                    "Encounter",
                    OWNER,
                    "context.encounter",
                )
            ),
        description = description?.value?.takeIf { it.isNotBlank() },
        typeText = type.text?.value.validatedText("type.text"),
        periodStart = periodStart,
        periodEnd = periodEnd,
        relatedQuestionnaireResponseId = context.relatedQuestionnaireResponseId(),
        authorHprs = author.map { parsePractitionerHpr(it.reference?.value, OWNER) }.distinct(),
        attachment = content.attachment.toNewAttachment(),
    )
}

fun ClinicalDocument.toDocumentReference(hprs: List<String>): DocumentReference {
    val attachment = attachment
    return DocumentReference(
        id = note.id.value.toString(),
        status = Enumeration(value = DocumentReferenceStatus.Current),
        type =
            CodeableConcept(
                coding =
                    listOf(
                        Coding(
                            system = Uri(value = CLINICAL_NOTE_TYPE_SYSTEM),
                            code = Code(value = CLINICAL_NOTE_TYPE_CODE),
                            display = FhirString(value = CLINICAL_NOTE_TYPE_DISPLAY),
                        )
                    ),
                text = typeText?.let { FhirString(value = it) },
            ),
        subject = Reference(reference = FhirString(value = "Patient/${note.patientId.value}")),
        author = hprs.map { Reference(reference = FhirString(value = "Practitioner/$it")) },
        description = note.clinicalNote?.let { FhirString(value = it) },
        content =
            listOf(
                DocumentReference.Content(
                    attachment =
                        Attachment(
                            contentType = Code(value = attachment?.contentType ?: PDF_CONTENT_TYPE),
                            title = attachment?.title?.let { FhirString(value = it) },
                            language =
                                attachment?.language?.let { stored ->
                                    CommonLanguages.entries
                                        .firstOrNull { it.getCode() == stored }
                                        ?.let { Enumeration(value = it) }
                                },
                            data =
                                attachment?.data?.let {
                                    Base64Binary(value = Base64.getEncoder().encodeToString(it))
                                },
                        )
                )
            ),
        context =
            DocumentReference.Context(
                encounter =
                    listOf(
                        Reference(
                            reference = FhirString(value = "Encounter/${note.consultationId.value}")
                        )
                    ),
                period =
                    if (periodStart != null || periodEnd != null) {
                        Period(start = periodStart.toFhirDate(), end = periodEnd.toFhirDate())
                    } else {
                        null
                    },
                related =
                    listOfNotNull(
                        relatedQuestionnaireResponseId?.let {
                            Reference(reference = FhirString(value = "QuestionnaireResponse/$it"))
                        }
                    ),
            ),
    )
}

private fun DocumentReference.rejectUnsupportedFields() {
    val unsupported = buildList {
        if (masterIdentifier != null) add("masterIdentifier")
        if (identifier.isNotEmpty()) add("identifier")
        if (docStatus != null) add("docStatus")
        if (category.isNotEmpty()) add("category")
        if (date != null) add("date")
        if (authenticator != null) add("authenticator")
        if (custodian != null) add("custodian")
        if (relatesTo.isNotEmpty()) add("relatesTo")
        if (securityLabel.isNotEmpty()) add("securityLabel")
        if (extension.isNotEmpty()) add("extension")
        if (modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    rejectFields(OWNER, unsupported)
}

private fun DocumentReference.Content.rejectUnsupportedFields() {
    val unsupported = buildList {
        if (format != null) add("format")
        if (extension.isNotEmpty()) add("extension")
        if (modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    rejectFields("$OWNER.content", unsupported)
}

private fun DocumentReference.Context.rejectUnsupportedFields() {
    val unsupported = buildList {
        if (event.isNotEmpty()) add("event")
        if (facilityType != null) add("facilityType")
        if (practiceSetting != null) add("practiceSetting")
        if (sourcePatientInfo != null) add("sourcePatientInfo")
        if (extension.isNotEmpty()) add("extension")
        if (modifierExtension.isNotEmpty()) add("modifierExtension")
    }
    rejectFields("$OWNER.context", unsupported)
}

private fun rejectFields(path: String, fields: List<String>) {
    if (fields.isNotEmpty()) {
        invalidResource(
            OperationOutcome.IssueType.Not_Supported,
            "$path.${fields.joinToString(", ")} støttes ikke",
        )
    }
}

private fun DocumentReference.Context.relatedQuestionnaireResponseId() =
    related
        .also {
            if (it.size > 1) {
                invalidResource(
                    OperationOutcome.IssueType.Invalid,
                    "$OWNER.context.related kan inneholde maksimalt én referanse",
                )
            }
        }
        .singleOrNull()
        ?.let {
            parseReferenceId(it.reference?.value, "QuestionnaireResponse", OWNER, "context.related")
        }

private fun Attachment.toNewAttachment(): NewAttachment? {
    if (contentType?.value != PDF_CONTENT_TYPE) {
        invalidResource(
            OperationOutcome.IssueType.Value,
            "$OWNER.content.attachment.contentType må være '$PDF_CONTENT_TYPE'",
        )
    }
    val unsupported = buildList {
        if (url != null) add("url")
        if (this@toNewAttachment.size != null) add("size")
        if (hash != null) add("hash")
        if (creation != null) add("creation")
        if (extension.isNotEmpty()) add("extension")
    }
    rejectFields("$OWNER.content.attachment", unsupported)

    val base64 = data?.value
    if (base64 == null) {
        if (title != null || language != null) {
            invalidResource(
                OperationOutcome.IssueType.Invalid,
                "$OWNER.content.attachment.title og language krever attachment.data",
            )
        }
        return null
    }
    if (base64.length > MAX_ATTACHMENT_BASE64_CHARS) attachmentTooLarge()
    val bytes =
        try {
            Base64.getDecoder().decode(base64)
        } catch (exception: IllegalArgumentException) {
            invalidResource(
                OperationOutcome.IssueType.Value,
                "$OWNER.content.attachment.data er ikke gyldig base64: ${exception.message}",
            )
        }
    if (bytes.size > MAX_ATTACHMENT_BYTES) attachmentTooLarge()
    if (!bytes.startsWith(PDF_SIGNATURE)) {
        invalidResource(
            OperationOutcome.IssueType.Value,
            "$OWNER.content.attachment.data må være en PDF",
        )
    }
    return NewAttachment(
        title = title?.value.validatedText("content.attachment.title"),
        language = language?.value?.getCode(),
        data = bytes,
    )
}

private fun attachmentTooLarge(): Nothing =
    fhirFailure(
        HttpStatusCode.PayloadTooLarge,
        OperationOutcome.IssueType.Too_Long,
        "$OWNER.content.attachment.data er større enn $MAX_ATTACHMENT_BYTES bytes",
    )

private fun String?.validatedText(field: String): String? {
    val text = this?.takeIf { it.isNotBlank() } ?: return null
    if (text.length > MAX_TEXT_LENGTH || text.any { it.isISOControl() }) {
        invalidResource(
            OperationOutcome.IssueType.Invalid,
            "$OWNER.$field kan ikke være lengre enn $MAX_TEXT_LENGTH tegn eller inneholde " +
                "kontrolltegn",
        )
    }
    return text
}

private fun DateTime?.toLocalDate(field: String): LocalDate? =
    when (val value = this?.value) {
        null -> null
        is FhirDateTime.Date -> value.date.toJavaLocalDate()
        is FhirDateTime.DateTime -> value.dateTime.date.toJavaLocalDate()
        else ->
            invalidResource(
                OperationOutcome.IssueType.Invalid,
                "$OWNER.$field må være en dato (YYYY-MM-DD)",
            )
    }

private fun LocalDate?.toFhirDate(): DateTime? =
    this?.let { DateTime(value = FhirDateTime.Date(it.toKotlinLocalDate())) }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
