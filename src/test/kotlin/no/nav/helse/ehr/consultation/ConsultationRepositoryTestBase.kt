package no.nav.helse.ehr.consultation

import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.patient.Patient
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.patient.PatientRepository
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.WithPostgresql

abstract class ConsultationRepositoryTestBase : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    protected val consultationRepository = ConsultationRepository()
    protected val patientRepository = PatientRepository()

    @OptIn(ExperimentalUuidApi::class)
    protected suspend fun createPatient(
        patientId: PatientId = PatientId(Uuid.generateV4()),
        hpr: PractitionerHpr = PractitionerHpr("123"),
    ): PatientId {
        patientRepository.insert(
            Patient(
                id = patientId,
                givenName = "givenName",
                familyName = "familyName",
                personident = "personident-${patientId.value}",
            )
        )
        return patientId
    }

    protected suspend fun createConsultation(patientId: PatientId): ConsultationId =
        consultationRepository.insert(
            CreateConsultation(
                patientId,
                listOf(PractitionerHpr("123")),
                LocalDateTime.now(),
                ConsultationStatus.IN_PROGRESS,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
}
