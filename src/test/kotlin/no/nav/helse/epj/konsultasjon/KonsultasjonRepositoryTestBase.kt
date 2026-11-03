package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.patient.Patient
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.patient.PatientRepository
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.WithPostgresql

abstract class KonsultasjonRepositoryTestBase : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    protected val konsultasjonRepository = KonsultasjonRepository()
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

    protected suspend fun opprettKonsultasjon(patientId: PatientId): KonsultasjonId =
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                patientId,
                listOf(PractitionerHpr("123")),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
}
