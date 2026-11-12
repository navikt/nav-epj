package no.nav.helse.fhir.questionnaireresponse

import kotlin.uuid.Uuid
import no.nav.helse.core.db.QuestionnaireResponseTable
import no.nav.helse.core.db.consultationIdsAtOrg
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.ClinicalDocumentConflictException
import no.nav.helse.core.utils.ClinicalDocumentWriteNotAllowedException
import no.nav.helse.core.utils.DuplicateClinicalNoteException
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.requireConsultationForWrite
import no.nav.helse.ehr.patient.PatientId
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class QuestionnaireResponseWrite(
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val authorHprs: List<String>,
    val resourceJson: String,
)

class StoredQuestionnaireResponse(
    val id: Uuid,
    val patientId: PatientId,
    val consultationId: ConsultationId,
    val resourceJson: String,
)

class QuestionnaireResponseRepository {

    suspend fun write(
        id: Uuid,
        write: QuestionnaireResponseWrite,
        orgNumber: String?,
        allowCreate: Boolean,
        allowUpdate: Boolean,
    ): Boolean = dbQuery {
        var existing = lock(id)
        if (existing == null) {
            if (!allowCreate) throw ClinicalDocumentWriteNotAllowedException(creating = true)
            write.requireConsultation(orgNumber)
            val inserted =
                QuestionnaireResponseTable.insertIgnore {
                        it[QuestionnaireResponseTable.id] = id
                        it[patientId] = write.patientId.value
                        it[consultationId] = write.consultationId.value
                        it[resource] = write.resourceJson
                    }
                    .insertedCount
            if (inserted == 1) return@dbQuery true
            existing = lock(id) ?: throw DuplicateClinicalNoteException()
        }
        if (!allowUpdate) throw ClinicalDocumentWriteNotAllowedException(creating = false)
        write.requireConsultation(orgNumber)
        if (
            existing.patientId != write.patientId.value ||
                existing.consultationId != write.consultationId.value
        ) {
            throw ClinicalDocumentConflictException()
        }
        QuestionnaireResponseTable.update({ QuestionnaireResponseTable.id eq id }) {
            it[resource] = write.resourceJson
            it[updatedAt] = CurrentTimestamp
        }
        false
    }

    suspend fun find(id: Uuid, orgNumber: String?): StoredQuestionnaireResponse? = dbQuery {
        QuestionnaireResponseTable.selectAll()
            .where { (QuestionnaireResponseTable.id eq id) and atOrg(orgNumber) }
            .singleOrNull()
            ?.toStored()
    }

    suspend fun list(
        patientId: PatientId,
        consultationId: ConsultationId?,
        orgNumber: String?,
    ): List<StoredQuestionnaireResponse> = dbQuery {
        QuestionnaireResponseTable.selectAll()
            .where {
                var condition =
                    (QuestionnaireResponseTable.patientId eq patientId.value) and atOrg(orgNumber)
                if (consultationId != null) {
                    condition =
                        condition and
                            (QuestionnaireResponseTable.consultationId eq consultationId.value)
                }
                condition
            }
            .orderBy(
                QuestionnaireResponseTable.createdAt to SortOrder.ASC,
                QuestionnaireResponseTable.id to SortOrder.ASC,
            )
            .map { it.toStored() }
    }

    suspend fun delete(id: Uuid, patientId: PatientId, orgNumber: String?): Boolean = dbQuery {
        QuestionnaireResponseTable.deleteWhere {
            (QuestionnaireResponseTable.id eq id) and
                (QuestionnaireResponseTable.patientId eq patientId.value) and
                atOrg(orgNumber)
        } == 1
    }

    private data class LockedRow(val patientId: Uuid, val consultationId: Uuid)

    private fun lock(id: Uuid): LockedRow? =
        QuestionnaireResponseTable.selectAll()
            .where { QuestionnaireResponseTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.let {
                LockedRow(
                    it[QuestionnaireResponseTable.patientId],
                    it[QuestionnaireResponseTable.consultationId],
                )
            }

    private fun atOrg(orgNumber: String?): Op<Boolean> =
        QuestionnaireResponseTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber)

    private fun QuestionnaireResponseWrite.requireConsultation(orgNumber: String?) =
        requireConsultationForWrite(consultationId, patientId, authorHprs, orgNumber)

    private fun ResultRow.toStored() =
        StoredQuestionnaireResponse(
            id = this[QuestionnaireResponseTable.id],
            patientId = PatientId(this[QuestionnaireResponseTable.patientId]),
            consultationId = ConsultationId(this[QuestionnaireResponseTable.consultationId]),
            resourceJson = this[QuestionnaireResponseTable.resource],
        )
}
