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
import kotlin.text.substringAfter
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.konsultasjon.Journalnotat
import no.nav.helse.epj.konsultasjon.JournalnotatId
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.OpprettJournalnotatRequest
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

private const val JOURNALNOTAT_TYPE_SYSTEM = "urn:oid:2.16.578.1.12.4.1.1.9602"
private const val JOURNALNOTAT_TYPE_CODE = "J01-2"
private const val JOURNALNOTAT_TYPE_DISPLAY = "Sykmeldinger og trygdesaker"
private const val JOURNALNOTAT_CONTENT_TYPE = "application/pdf"

/**
 * Raised when an incoming DocumentReference does not fit the narrow journal-note shape backed by
 * [Journalnotat]. Carries the FHIR [OperationOutcome.IssueType] so the route can turn it into a
 * matching OperationOutcome without re-deriving the classification.
 */
class InvalidDocumentReferenceException(
    val issueType: OperationOutcome.IssueType,
    message: kotlin.String,
) : RuntimeException(message)

class DocumentReferenceService(
    val konsultasjonService: KonsultasjonService,
    val helsepersonellService: HelsepersonellService,
) {

    val log = logger()

    suspend fun createDocumentReference(request: OpprettJournalnotatRequest): DocumentReference {
        val journalnotat = konsultasjonService.opprettJournalnotat(request)
        val hpr = helsepersonellService.getHelsepersonell(journalnotat.pasientId)
        return journalnotat.toDocumentReference(hpr)
    }

    suspend fun searchDocumentReferences(
        patientId: PatientInputId,
        encounterId: EncounterId?,
    ): Bundle {
        val journalnotater =
            konsultasjonService.getJournalnotater(
                PasientId(patientId.value),
                encounterId?.let { KonsultasjonId(it.value) },
            )
        val hpr = helsepersonellService.getHelsepersonell(PasientId(patientId.value))

        return Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                journalnotater.map { journalnotat ->
                    val documentReference = journalnotat.toDocumentReference(hpr)
                    Bundle.Entry(
                        fullUrl = Uri(value = "DocumentReference/${documentReference.id}"),
                        resource = documentReference,
                    )
                },
        )
    }

    suspend fun createDocumentReference(documentReference: DocumentReference): Boolean {
        val konsultasjonId =
            documentReference.context
                ?.encounter
                ?.first()
                ?.reference
                ?.value
                ?.substringAfter("Encounter/")
        val pasientId = documentReference.subject?.reference?.value?.substringAfter("Patient/")

        val createJournalnotat =
            Journalnotat(
                id =
                    JournalnotatId(
                        Uuid.parse(
                            requireNotNull(documentReference.id) { "DocumentReference mangler id" }
                        )
                    ),
                konsultasjonId =
                    KonsultasjonId(
                        Uuid.parse(
                            requireNotNull(konsultasjonId) {
                                "DocumentReference mangler context.encounter.reference"
                            }
                        )
                    ),
                pasientId =
                    PasientId(
                        Uuid.parse(
                            requireNotNull(pasientId) {
                                "DocumentReference mangler subject.reference"
                            }
                        )
                    ),
                journalnotat =
                    requireNotNull(documentReference.description?.value) {
                        "DocumentReference mangler description"
                    },
            )

        return konsultasjonService.createJournalnotat(createJournalnotat)
    }

    suspend fun getDocumentReferences(
        documentReferenceId: DocumentReferenceId
    ): DocumentReference? {
        val journalnotat =
            konsultasjonService.getJournalnotat(JournalnotatId(documentReferenceId.value))
                ?: return null
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
                                system = Uri(value = JOURNALNOTAT_TYPE_SYSTEM),
                                code = Code(value = JOURNALNOTAT_TYPE_CODE),
                                display = String(value = JOURNALNOTAT_TYPE_DISPLAY),
                            )
                        )
                ),
            content =
                listOf(
                    DocumentReference.Content(
                        attachment =
                            Attachment(
                                title = String(value = "tittel generert av Nav"),
                                language = Enumeration(value = CommonLanguages.No_No),
                                contentType = Code(value = JOURNALNOTAT_CONTENT_TYPE),
                                data = Base64Binary(value = "base64 PDF"),
                            )
                    )
                ),
            subject = Reference(reference = String(value = "Patient/${this.pasientId.value}")),
            author = hpr.map { Reference(reference = String(value = "Practitioner/${it.value}")) },
            context =
                DocumentReference.Context(
                    encounter =
                        listOf(
                            Reference(
                                reference = String(value = "Encounter/${this.konsultasjonId.value}")
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
 * [Journalnotat]. The client-supplied `id`, if any, is intentionally never read here: create always
 * assigns a server-generated id, per FHIR create semantics. `status`, `type` and `content` are not
 * persisted; they are always regenerated on read, so they are validated against the only shape the
 * server can honestly reproduce rather than silently discarded.
 */
fun DocumentReference.toOpprettJournalnotatRequest(): OpprettJournalnotatRequest {
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
        typeCoding.system?.value != JOURNALNOTAT_TYPE_SYSTEM ||
            typeCoding.code?.value != JOURNALNOTAT_TYPE_CODE
    ) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.type støtter kun system '$JOURNALNOTAT_TYPE_SYSTEM' " +
                "og kode '$JOURNALNOTAT_TYPE_CODE'",
        )
    }

    if (this.content.size != 1) {
        invalid(
            OperationOutcome.IssueType.Invalid,
            "DocumentReference.content må inneholde nøyaktig én oppføring",
        )
    }
    val content = this.content.single()
    if (content.format != null) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.content.format støttes ikke",
        )
    }
    val attachment = content.attachment
    if (attachment.contentType?.value != JOURNALNOTAT_CONTENT_TYPE) {
        invalid(
            OperationOutcome.IssueType.Value,
            "DocumentReference.content.attachment.contentType må være " +
                "'$JOURNALNOTAT_CONTENT_TYPE'",
        )
    }
    val unsupportedAttachmentFields = buildList {
        if (attachment.language != null) add("language")
        if (attachment.data != null) add("data")
        if (attachment.url != null) add("url")
        if (attachment.size != null) add("size")
        if (attachment.hash != null) add("hash")
        if (attachment.title != null) add("title")
        if (attachment.creation != null) add("creation")
    }
    if (unsupportedAttachmentFields.isNotEmpty()) {
        invalid(
            OperationOutcome.IssueType.Not_Supported,
            "DocumentReference.content.attachment.${unsupportedAttachmentFields.joinToString(", ")} " +
                "støttes ikke",
        )
    }

    val pasientId = parseReferenceId(this.subject?.reference?.value, "Patient", "subject")

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
    val konsultasjonId =
        parseReferenceId(
            context.encounter.single().reference?.value,
            "Encounter",
            "context.encounter",
        )

    val journalnotat =
        this.description?.value?.takeIf { it.isNotBlank() }
            ?: invalid(
                OperationOutcome.IssueType.Required,
                "DocumentReference.description er påkrevd",
            )

    return OpprettJournalnotatRequest(
        pasientId = PasientId(pasientId),
        konsultasjonId = KonsultasjonId(konsultasjonId),
        journalnotat = journalnotat,
    )
}

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
 * Rejects every DocumentReference element that [Journalnotat] cannot represent, so unsupported
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
