package no.nav.helse.epj.measurement

import no.nav.helse.core.db.ConsultationTable
import no.nav.helse.core.db.MeasurementTable
import no.nav.helse.core.db.consultationAtOrg
import no.nav.helse.core.db.consultationIdsAtOrg
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.DuplicateMeasurementException
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

class MeasurementRepository {
    private val logger = logger()

    suspend fun insert(measurement: Measurement, orgNumber: String): Unit = dbQuery {
        val consultationPatientId =
            ConsultationTable.select(ConsultationTable.patientId)
                .where {
                    (ConsultationTable.id eq measurement.consultationId.value) and
                        consultationAtOrg(orgNumber)
                }
                .singleOrNull()
                ?.get(ConsultationTable.patientId)
                ?: throw ConsultationNotFoundException(measurement.consultationId)

        if (consultationPatientId != measurement.patientId.value) {
            throw ConsultationBelongsToOtherPatientException(
                measurement.consultationId,
                measurement.patientId,
            )
        }

        logger.info("Inserting measurement with id: ${measurement.id.value}")

        val insertedCount =
            MeasurementTable.insertIgnore {
                    it[id] = measurement.id.value
                    it[patientId] = measurement.patientId.value
                    it[consultationId] = measurement.consultationId.value
                    it[hpr] = measurement.hpr?.value
                    it[loincCode] = measurement.loincCode
                    it[loincDisplay] = measurement.loincDisplay
                    it[value] = measurement.value
                    it[unitCode] = measurement.unitCode
                    it[unitDisplay] = measurement.unitDisplay
                    it[effectiveAt] = measurement.effectiveAt
                    it[status] = measurement.status
                }
                .insertedCount

        if (insertedCount == 0) {
            throw DuplicateMeasurementException()
        }
    }

    suspend fun findById(id: MeasurementId, orgNumber: String?): Measurement? = dbQuery {
        MeasurementTable.selectAll()
            .where {
                (MeasurementTable.id eq id.value) and
                    (MeasurementTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
            }
            .singleOrNull()
            ?.toMeasurement()
    }

    suspend fun listByPatientId(patientId: PatientId, orgNumber: String?): List<Measurement> =
        dbQuery {
            MeasurementTable.selectAll()
                .where {
                    (MeasurementTable.patientId eq patientId.value) and
                        (MeasurementTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
                }
                .orderBy(MeasurementTable.effectiveAt, SortOrder.ASC)
                .map { it.toMeasurement() }
        }

    suspend fun listByConsultationId(
        consultationId: ConsultationId,
        orgNumber: String?,
    ): List<Measurement> = dbQuery {
        MeasurementTable.selectAll()
            .where {
                (MeasurementTable.consultationId eq consultationId.value) and
                    (MeasurementTable.consultationId inSubQuery consultationIdsAtOrg(orgNumber))
            }
            .orderBy(MeasurementTable.effectiveAt, SortOrder.ASC)
            .map { it.toMeasurement() }
    }

    private fun ResultRow.toMeasurement(): Measurement =
        Measurement(
            id = MeasurementId(this[MeasurementTable.id]),
            patientId = PatientId(this[MeasurementTable.patientId]),
            consultationId = ConsultationId(this[MeasurementTable.consultationId]),
            hpr = this[MeasurementTable.hpr]?.let { PractitionerHpr(it) },
            loincCode = this[MeasurementTable.loincCode],
            loincDisplay = this[MeasurementTable.loincDisplay],
            value = this[MeasurementTable.value],
            unitCode = this[MeasurementTable.unitCode],
            unitDisplay = this[MeasurementTable.unitDisplay],
            effectiveAt = this[MeasurementTable.effectiveAt],
            status = this[MeasurementTable.status],
        )
}
