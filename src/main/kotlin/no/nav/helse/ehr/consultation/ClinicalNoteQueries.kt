package no.nav.helse.ehr.consultation

import java.time.Duration
import java.time.Instant
import no.nav.helse.core.db.ClinicalNoteAttachmentTable
import no.nav.helse.core.db.ClinicalNoteTable
import no.nav.helse.ehr.patient.PatientId
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select

val ATTACHMENT_RETENTION: Duration = Duration.ofDays(14)

internal fun attachmentCutoff(): Instant = Instant.now().minus(ATTACHMENT_RETENTION)

private val noteWithAttachment =
    ClinicalNoteTable.join(
        ClinicalNoteAttachmentTable,
        JoinType.LEFT,
        onColumn = ClinicalNoteTable.id,
        otherColumn = ClinicalNoteAttachmentTable.clinicalNoteId,
    )

private val noteWithAttachmentMetadataColumns =
    ClinicalNoteTable.columns +
        listOf(
            ClinicalNoteAttachmentTable.clinicalNoteId,
            ClinicalNoteAttachmentTable.title,
            ClinicalNoteAttachmentTable.language,
            ClinicalNoteAttachmentTable.contentType,
            ClinicalNoteAttachmentTable.createdAt,
        )

internal fun clinicalNoteMetadataQuery(): Query =
    noteWithAttachment.select(noteWithAttachmentMetadataColumns)

internal fun clinicalNoteAttachmentDownloadQuery(): Query =
    noteWithAttachment.select(
        ClinicalNoteAttachmentTable.title,
        ClinicalNoteAttachmentTable.data,
        ClinicalNoteTable.id,
    )

internal fun ResultRow.attachmentCreatedAt(): Instant? =
    getOrNull(ClinicalNoteAttachmentTable.createdAt)

internal fun ResultRow.toClinicalNote(cutoff: Instant): ClinicalNote {
    val attachmentCreatedAt = attachmentCreatedAt()
    return ClinicalNote(
        id = ClinicalNoteId(this[ClinicalNoteTable.id]),
        consultationId = ConsultationId(this[ClinicalNoteTable.consultationId]),
        patientId = PatientId(this[ClinicalNoteTable.patientId]),
        clinicalNote = this[ClinicalNoteTable.clinicalNote],
        source = this[ClinicalNoteTable.origin],
        attachmentTitle = getOrNull(ClinicalNoteAttachmentTable.title),
        hasAttachment = attachmentCreatedAt != null,
        attachmentExpired = attachmentCreatedAt?.let { !it.isAfter(cutoff) } ?: false,
    )
}
