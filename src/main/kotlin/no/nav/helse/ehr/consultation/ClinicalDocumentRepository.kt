package no.nav.helse.ehr.consultation

import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicalNoteAttachmentTable
import no.nav.helse.core.db.ClinicalNoteTable
import no.nav.helse.core.db.QuestionnaireResponseTable
import no.nav.helse.core.db.consultationIdsAtOrg
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.AttachmentNotRetainedException
import no.nav.helse.core.utils.ClinicalDocumentConflictException
import no.nav.helse.core.utils.ClinicalDocumentWriteNotAllowedException
import no.nav.helse.core.utils.DuplicateClinicalNoteException
import no.nav.helse.ehr.patient.PatientId
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

const val PDF_CONTENT_TYPE = "application/pdf"

private class LockedNote(
    val consultationId: Uuid,
    val patientId: Uuid,
    val source: ClinicalNoteSource,
    val relatedQuestionnaireResponseId: Uuid?,
)

class ClinicalDocumentRepository {

    suspend fun write(
        id: ClinicalNoteId,
        write: ClinicalDocumentWrite,
        orgNumber: String?,
        allowCreate: Boolean,
        allowUpdate: Boolean,
    ): ClinicalDocumentWriteResult = dbQuery {
        var existing = lockNote(id.value)
        if (existing == null) {
            if (write.retainedAttachment != null) throw AttachmentNotRetainedException()
            if (!allowCreate) throw ClinicalDocumentWriteNotAllowedException(creating = true)
            requireConsultationForWrite(
                write.consultationId,
                write.patientId,
                write.authorHprs,
                orgNumber,
            )
            if (insertNote(id, write) == 1) {
                replaceAttachment(id, write.attachment)
                return@dbQuery ClinicalDocumentWriteResult(id, created = true)
            }
            existing = lockNote(id.value) ?: throw DuplicateClinicalNoteException()
        }
        if (!allowUpdate) throw ClinicalDocumentWriteNotAllowedException(creating = false)
        requireConsultationForWrite(
            write.consultationId,
            write.patientId,
            write.authorHprs,
            orgNumber,
        )
        if (
            existing.source != ClinicalNoteSource.FHIR ||
                existing.consultationId != write.consultationId.value ||
                existing.patientId != write.patientId.value
        ) {
            throw ClinicalDocumentConflictException()
        }
        ClinicalNoteTable.update({ ClinicalNoteTable.id eq id.value }) {
            it[clinicalNote] = write.description
            it[typeText] = write.typeText
            it[periodStart] = write.periodStart
            it[periodEnd] = write.periodEnd
            it[relatedQuestionnaireResponseId] = write.relatedQuestionnaireResponseId
            it[authorHprs] = write.authorHprs
        }
        if (write.retainedAttachment != null) {
            requireExpiredAttachment(id, write.retainedAttachment)
        } else {
            replaceAttachment(id, write.attachment)
        }
        ClinicalDocumentWriteResult(id, created = false)
    }

    suspend fun find(id: ClinicalNoteId, orgNumber: String?): ClinicalDocument? = dbQuery {
        val cutoff = attachmentCutoff()
        val row =
            clinicalNoteMetadataQuery()
                .where {
                    (ClinicalNoteTable.id eq id.value) and
                        (ClinicalNoteTable.consultationId inSubQuery
                            consultationIdsAtOrg(orgNumber))
                }
                .singleOrNull() ?: return@dbQuery null
        val data = attachmentData(listOf(id.value), cutoff)
        row.toClinicalDocument(cutoff, data)
    }

    suspend fun list(
        patientId: PatientId,
        consultationId: ConsultationId?,
        relatedQuestionnaireResponseId: Uuid?,
        orgNumber: String?,
    ): List<ClinicalDocument> = dbQuery {
        val cutoff = attachmentCutoff()
        val rows =
            clinicalNoteMetadataQuery()
                .where {
                    var condition =
                        (ClinicalNoteTable.patientId eq patientId.value) and
                            (ClinicalNoteTable.consultationId inSubQuery
                                consultationIdsAtOrg(orgNumber))
                    if (consultationId != null) {
                        condition =
                            condition and (ClinicalNoteTable.consultationId eq consultationId.value)
                    }
                    if (relatedQuestionnaireResponseId != null) {
                        condition =
                            condition and
                                (ClinicalNoteTable.relatedQuestionnaireResponseId eq
                                    relatedQuestionnaireResponseId)
                    }
                    condition
                }
                .orderBy(
                    ClinicalNoteTable.createdAt to SortOrder.ASC,
                    ClinicalNoteTable.id to SortOrder.ASC,
                )
                .toList()
        val data = attachmentData(rows.map { it[ClinicalNoteTable.id] }, cutoff)
        rows.map { it.toClinicalDocument(cutoff, data) }
    }

    suspend fun findAttachment(id: ClinicalNoteId, orgNumber: String?): ClinicalNoteAttachment? =
        dbQuery {
            clinicalNoteAttachmentDownloadQuery()
                .where {
                    (ClinicalNoteTable.id eq id.value) and
                        (ClinicalNoteTable.consultationId inSubQuery
                            consultationIdsAtOrg(orgNumber)) and
                        (ClinicalNoteAttachmentTable.createdAt greater attachmentCutoff()) and
                        ClinicalNoteAttachmentTable.data.isNotNull()
                }
                .singleOrNull()
                ?.let {
                    ClinicalNoteAttachment(
                        it[ClinicalNoteAttachmentTable.title],
                        it[ClinicalNoteAttachmentTable.data]!!,
                    )
                }
        }

    suspend fun deleteFhirDocument(
        id: ClinicalNoteId,
        patientId: PatientId,
        orgNumber: String?,
    ): ClinicalNoteDeleteResult = dbQuery {
        val note =
            lockNote(id.value, atOrg(orgNumber))?.takeIf { it.patientId == patientId.value }
                ?: return@dbQuery ClinicalNoteDeleteResult.NOT_FOUND
        if (note.source != ClinicalNoteSource.FHIR)
            return@dbQuery ClinicalNoteDeleteResult.PROTECTED
        ClinicalNoteTable.deleteWhere { ClinicalNoteTable.id eq id.value }
        ClinicalNoteDeleteResult.DELETED
    }

    suspend fun deleteFhirDocumentWithQuestionnaireResponse(
        id: ClinicalNoteId,
        orgNumber: String?,
    ): ClinicalNoteDeleteResult = dbQuery {
        val note =
            lockNote(id.value, atOrg(orgNumber))
                ?: return@dbQuery ClinicalNoteDeleteResult.NOT_FOUND
        if (note.source != ClinicalNoteSource.FHIR)
            return@dbQuery ClinicalNoteDeleteResult.PROTECTED
        note.relatedQuestionnaireResponseId?.let { questionnaireResponseId ->
            QuestionnaireResponseTable.deleteWhere {
                (QuestionnaireResponseTable.id eq questionnaireResponseId) and
                    (QuestionnaireResponseTable.patientId eq note.patientId) and
                    (QuestionnaireResponseTable.consultationId eq note.consultationId)
            }
        }
        ClinicalNoteTable.deleteWhere { ClinicalNoteTable.id eq id.value }
        ClinicalNoteDeleteResult.DELETED
    }

    private fun atOrg(orgNumber: String?): Op<Boolean> =
        ClinicalNoteTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber)

    private fun lockNote(id: Uuid, scope: Op<Boolean> = Op.TRUE): LockedNote? =
        ClinicalNoteTable.selectAll()
            .where { (ClinicalNoteTable.id eq id) and scope }
            .forUpdate()
            .singleOrNull()
            ?.let {
                LockedNote(
                    consultationId = it[ClinicalNoteTable.consultationId],
                    patientId = it[ClinicalNoteTable.patientId],
                    source = it[ClinicalNoteTable.origin],
                    relatedQuestionnaireResponseId =
                        it[ClinicalNoteTable.relatedQuestionnaireResponseId],
                )
            }

    private fun insertNote(id: ClinicalNoteId, write: ClinicalDocumentWrite): Int =
        ClinicalNoteTable.insertIgnore {
                it[ClinicalNoteTable.id] = id.value
                it[consultationId] = write.consultationId.value
                it[patientId] = write.patientId.value
                it[clinicalNote] = write.description
                it[origin] = ClinicalNoteSource.FHIR
                it[typeText] = write.typeText
                it[periodStart] = write.periodStart
                it[periodEnd] = write.periodEnd
                it[relatedQuestionnaireResponseId] = write.relatedQuestionnaireResponseId
                it[authorHprs] = write.authorHprs
            }
            .insertedCount

    private fun requireExpiredAttachment(id: ClinicalNoteId, retained: RetainedAttachment) {
        val stored =
            ClinicalNoteAttachmentTable.selectAll()
                .where { ClinicalNoteAttachmentTable.clinicalNoteId eq id.value }
                .singleOrNull()
        val expired =
            stored != null &&
                !stored[ClinicalNoteAttachmentTable.createdAt].isAfter(attachmentCutoff())
        if (
            !expired ||
                (retained.title != null &&
                    retained.title != stored[ClinicalNoteAttachmentTable.title]) ||
                (retained.language != null &&
                    retained.language != stored[ClinicalNoteAttachmentTable.language])
        ) {
            throw AttachmentNotRetainedException()
        }
    }

    private fun replaceAttachment(id: ClinicalNoteId, attachment: NewAttachment?) {
        ClinicalNoteAttachmentTable.deleteWhere { clinicalNoteId eq id.value }
        if (attachment == null) return
        ClinicalNoteAttachmentTable.insert {
            it[clinicalNoteId] = id.value
            it[title] = attachment.title
            it[language] = attachment.language
            it[contentType] = PDF_CONTENT_TYPE
            it[sizeBytes] = attachment.data.size
            it[data] = attachment.data
        }
        ClinicalNoteAttachmentTable.update({
            (ClinicalNoteAttachmentTable.createdAt lessEq attachmentCutoff()) and
                ClinicalNoteAttachmentTable.data.isNotNull()
        }) {
            it[data] = null
        }
    }

    private fun attachmentData(ids: List<Uuid>, cutoff: Instant): Map<Uuid, ByteArray> =
        ClinicalNoteAttachmentTable.select(
                ClinicalNoteAttachmentTable.clinicalNoteId,
                ClinicalNoteAttachmentTable.data,
            )
            .where {
                (ClinicalNoteAttachmentTable.clinicalNoteId inList ids) and
                    (ClinicalNoteAttachmentTable.createdAt greater cutoff) and
                    ClinicalNoteAttachmentTable.data.isNotNull()
            }
            .associate {
                it[ClinicalNoteAttachmentTable.clinicalNoteId] to
                    it[ClinicalNoteAttachmentTable.data]!!
            }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toClinicalDocument(
        cutoff: Instant,
        data: Map<Uuid, ByteArray>,
    ): ClinicalDocument {
        val note = toClinicalNote(cutoff)
        return ClinicalDocument(
            note = note,
            typeText = this[ClinicalNoteTable.typeText],
            periodStart = this[ClinicalNoteTable.periodStart],
            periodEnd = this[ClinicalNoteTable.periodEnd],
            relatedQuestionnaireResponseId = this[ClinicalNoteTable.relatedQuestionnaireResponseId],
            authorHprs = this[ClinicalNoteTable.authorHprs],
            attachment =
                if (note.hasAttachment) {
                    StoredAttachment(
                        title = getOrNull(ClinicalNoteAttachmentTable.title),
                        language = getOrNull(ClinicalNoteAttachmentTable.language),
                        contentType = this[ClinicalNoteAttachmentTable.contentType],
                        expired = note.attachmentExpired,
                        data = data[note.id.value],
                    )
                } else {
                    null
                },
        )
    }
}
