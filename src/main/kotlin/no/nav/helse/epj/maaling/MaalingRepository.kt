package no.nav.helse.epj.maaling

import no.nav.helse.core.db.KonsultasjonTable
import no.nav.helse.core.db.MaalingTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.db.konsultasjonAtOrg
import no.nav.helse.core.db.konsultasjonIdsAtOrg
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.DuplikatMaalingException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.konsultasjon.KonsultasjonId
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
        val konsultasjonPatientId =
            KonsultasjonTable.select(KonsultasjonTable.patientId)
                .where {
                    (KonsultasjonTable.id eq maaling.konsultasjonId.value) and
                        konsultasjonAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(KonsultasjonTable.patientId)
                ?: throw KonsultasjonNotFoundException(maaling.konsultasjonId)

        if (konsultasjonPatientId != maaling.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                maaling.konsultasjonId,
                maaling.patientId,
            )
        }

        logger.info("Inserting maaling with id: ${maaling.id.value}")

        val insertedCount =
            MaalingTable.insertIgnore {
                    it[id] = maaling.id.value
                    it[patientId] = maaling.patientId.value
                    it[konsultasjonId] = maaling.konsultasjonId.value
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
                    (MaalingTable.konsultasjonId inSubQuery konsultasjonIdsAtOrg(orgNumber))
            }
            .singleOrNull()
            ?.toMaaling()
    }

    suspend fun listByPatientId(patientId: PatientId, orgNumber: String?): List<Maaling> = dbQuery {
        MaalingTable.selectAll()
            .where {
                (MaalingTable.patientId eq patientId.value) and
                    (MaalingTable.konsultasjonId inSubQuery konsultasjonIdsAtOrg(orgNumber))
            }
            .orderBy(MaalingTable.effektivTidspunkt, SortOrder.ASC)
            .map { it.toMaaling() }
    }

    suspend fun listByKonsultasjonId(
        konsultasjonId: KonsultasjonId,
        orgNumber: String?,
    ): List<Maaling> = dbQuery {
        MaalingTable.selectAll()
            .where {
                (MaalingTable.konsultasjonId eq konsultasjonId.value) and
                    (MaalingTable.konsultasjonId inSubQuery konsultasjonIdsAtOrg(orgNumber))
            }
            .orderBy(MaalingTable.effektivTidspunkt, SortOrder.ASC)
            .map { it.toMaaling() }
    }

    private fun ResultRow.toMaaling(): Maaling =
        Maaling(
            id = MaalingId(this[MaalingTable.id]),
            patientId = PatientId(this[MaalingTable.patientId]),
            konsultasjonId = KonsultasjonId(this[MaalingTable.konsultasjonId]),
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
