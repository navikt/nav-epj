package no.nav.helse.epj.maaling

import no.nav.helse.core.db.ConsultationTable
import no.nav.helse.core.db.MaalingTable
import no.nav.helse.core.db.consultationAtOrg
import no.nav.helse.core.db.consultationIdsAtOrg
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.DuplikatMaalingException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class MaalingRepository {
    private val logger = logger()

    suspend fun insert(maaling: Maaling, orgNumber: String): Unit = dbQuery {
        val consultationPatientId =
            ConsultationTable.select(ConsultationTable.patientId)
                .where {
                    (ConsultationTable.id eq maaling.consultationId.value) and
                        consultationAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(ConsultationTable.patientId)
                ?: throw ConsultationNotFoundException(maaling.consultationId)

        if (consultationPatientId != maaling.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                maaling.consultationId,
                maaling.patientId,
            )
        }

        logger.info("Inserting maaling with id: ${maaling.id.value}")

        val insertedCount =
            MaalingTable.insertIgnore {
                    it[id] = maaling.id.value
                    it[patientId] = maaling.patientId.value
                    it[consultationId] = maaling.consultationId.value
                    it[hpr] = maaling.hpr?.value
                    it[loincKode] = maaling.loincKode
                    it[loincVisningsnavn] = maaling.loincVisningsnavn
                    it[verdi] = maaling.verdi
                    it[enhetKode] = maaling.enhetKode
                    it[enhetVisningsnavn] = maaling.enhetVisningsnavn
                    it[effektivTidspunkt] = maaling.effektivTidspunkt
                    it[status] = maaling.status
                }
                .insertedCount

        if (insertedCount == 0) {
            throw DuplikatMaalingException()
        }
    }

    suspend fun findById(id: MaalingId, orgNumber: String?): Maaling? = dbQuery {
        MaalingTable.selectAll()
            .where {
                (MaalingTable.id eq id.value) and
                    (MaalingTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
            }
            .singleOrNull()
            ?.toMaaling()
    }

    suspend fun listByPatientId(patientId: PatientId, orgNumber: String?): List<Maaling> = dbQuery {
        MaalingTable.selectAll()
            .where {
                (MaalingTable.patientId eq patientId.value) and
                    (MaalingTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
            }
            .orderBy(MaalingTable.effektivTidspunkt, SortOrder.ASC)
            .map { it.toMaaling() }
    }

    suspend fun listByConsultationId(
        consultationId: ConsultationId,
        orgNumber: String?,
    ): List<Maaling> = dbQuery {
        MaalingTable.selectAll()
            .where {
                (MaalingTable.consultationId eq consultationId.value) and
                    (MaalingTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
            }
            .orderBy(MaalingTable.effektivTidspunkt, SortOrder.ASC)
            .map { it.toMaaling() }
    }

    private fun ResultRow.toMaaling(): Maaling =
        Maaling(
            id = MaalingId(this[MaalingTable.id]),
            patientId = PatientId(this[MaalingTable.patientId]),
            consultationId = ConsultationId(this[MaalingTable.consultationId]),
            hpr = this[MaalingTable.hpr]?.let { PractitionerHpr(it) },
            loincKode = this[MaalingTable.loincKode],
            loincVisningsnavn = this[MaalingTable.loincVisningsnavn],
            verdi = this[MaalingTable.verdi],
            enhetKode = this[MaalingTable.enhetKode],
            enhetVisningsnavn = this[MaalingTable.enhetVisningsnavn],
            effektivTidspunkt = this[MaalingTable.effektivTidspunkt],
            status = this[MaalingTable.status],
        )
}
