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
    protected val clinicalDocumentRepository = ClinicalDocumentRepository()
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

    @OptIn(ExperimentalUuidApi::class)
    protected suspend fun writeFhirNote(
        patientId: PatientId,
        consultationId: ConsultationId,
        text: String?,
        id: ClinicalNoteId = ClinicalNoteId(Uuid.generateV4()),
        attachment: NewAttachment? = null,
        relatedQuestionnaireResponseId: Uuid? = null,
        orgNumber: String? = DEFAULT_ORG_NUMBER,
    ): ClinicalNoteId {
        clinicalDocumentRepository.write(
            id,
            ClinicalDocumentWrite(
                patientId = patientId,
                consultationId = consultationId,
                description = text,
                typeText = null,
                periodStart = null,
                periodEnd = null,
                relatedQuestionnaireResponseId = relatedQuestionnaireResponseId,
                authorHprs = emptyList(),
                attachment = attachment,
            ),
            orgNumber,
            allowCreate = true,
            allowUpdate = true,
        )
        return id
    }

    protected suspend fun createConsultation(
        patientId: PatientId,
        orgNumber: String = DEFAULT_ORG_NUMBER,
        hprs: List<String> = listOf("123"),
    ): ConsultationId =
        consultationRepository.insert(
            CreateConsultation(
                patientId,
                hprs.map { PractitionerHpr(it) },
                LocalDateTime.now(),
                ConsultationStatus.IN_PROGRESS,
                orgNumber = orgNumber,
            )
        )
}
