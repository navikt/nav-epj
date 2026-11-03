package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.tsm.diagnoser.Diagnose

class KonsultasjonService(private val konsultasjonRepository: KonsultasjonRepository) {
    val log = logger()

    suspend fun getKonsultasjoner(patientId: PatientId, orgNumber: String?): List<Konsultasjon> {
        return konsultasjonRepository.listByPatientId(patientId, orgNumber)
    }

    suspend fun getAktivKonsultasjon(patientId: PatientId, orgNumber: String?): Konsultasjon? {
        return konsultasjonRepository.findActiveByPatientId(patientId, orgNumber)
    }

    suspend fun getAktivKonsultasjon(
        patientId: PatientId,
        hpr: PractitionerHpr,
        orgNumber: String?,
    ): Konsultasjon? {
        return konsultasjonRepository.findActiveByPatientIdAndHpr(patientId, hpr, orgNumber)
    }

    suspend fun getKonsultasjon(konsultasjonId: KonsultasjonId, orgNumber: String?): Konsultasjon {
        return konsultasjonRepository.findByKonsultasjonId(konsultasjonId, orgNumber)
            ?: throw KonsultasjonNotFoundException(konsultasjonId)
    }

    suspend fun getDiagnoser(patientId: PatientId, orgNumber: String?): List<Diagnose> {
        return konsultasjonRepository.listDiagnoser(patientId, orgNumber)
    }

    suspend fun getDiagnoser(konsultasjonId: KonsultasjonId, orgNumber: String?): List<Diagnose> {
        return konsultasjonRepository.listDiagnoser(konsultasjonId, orgNumber)
    }

    suspend fun getJournalnotat(journalnotatId: JournalnotatId, orgNumber: String?): Journalnotat? {
        return konsultasjonRepository.findJournalnotat(journalnotatId, orgNumber)
    }

    suspend fun createJournalnotat(journalnotat: Journalnotat, orgNumber: String): Boolean {
        val insertedRows = konsultasjonRepository.insertJournalnotat(journalnotat, orgNumber)

        return insertedRows == 1
    }

    suspend fun getJournalnotater(
        patientId: PatientId,
        konsultasjonId: KonsultasjonId?,
        orgNumber: String?,
    ): List<Journalnotat> {
        return konsultasjonRepository.listJournalnotat(patientId, konsultasjonId, orgNumber)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun opprettJournalnotat(
        request: OpprettJournalnotatRequest,
        orgNumber: String,
    ): Journalnotat {
        val id = JournalnotatId(Uuid.generateV4())
        return konsultasjonRepository.opprettJournalnotat(id, request, orgNumber)
    }

    suspend fun createKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon): Konsultasjon {
        val createdId = konsultasjonRepository.insert(opprettKonsultasjon)
        val createdKonsultasjon =
            konsultasjonRepository.findByKonsultasjonId(createdId, opprettKonsultasjon.orgNumber)
                ?: throw IllegalStateException("Konsultasjon ble ikke opprettet")

        log.info(
            "Created konsultasjon id={} for patientId={}",
            createdKonsultasjon.id,
            opprettKonsultasjon.patientId,
        )
        return createdKonsultasjon
    }

    suspend fun getOrCreateKonsultasjon(
        patientId: PatientId,
        hpr: PractitionerHpr,
        orgNumber: String,
    ): Konsultasjon {
        val aktivKonsultasjon = getAktivKonsultasjon(patientId, hpr, orgNumber)
        if (aktivKonsultasjon != null) return aktivKonsultasjon
        val opprettKonsultasjon =
            OpprettKonsultasjon(
                patientId = patientId,
                hpr = listOf(hpr),
                startetTidspunkt = LocalDateTime.now(),
                status = KonsultasjonStatus.PAAGAAENDE,
                orgNumber = orgNumber,
            )
        return createKonsultasjon(opprettKonsultasjon)
    }

    suspend fun updateKonsultasjon(
        oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
        patientId: PatientId,
        orgNumber: String?,
    ) {
        log.info("oppdater konsultasjon på pasientId: $patientId")
        val updatedRows = konsultasjonRepository.update(oppdaterKonsultasjon, patientId, orgNumber)
        if (updatedRows == 0) {
            throw KonsultasjonNotFoundForPatientException(patientId)
        }
    }

    suspend fun cancelKonsultasjon(
        konsultasjonId: KonsultasjonId,
        patientId: PatientId,
        orgNumber: String?,
    ) {
        log.info("avbryter konsultasjon {} for pasientId: {}", konsultasjonId, patientId)
        val updatedRows = konsultasjonRepository.avbryt(konsultasjonId, patientId, orgNumber)
        if (updatedRows == 0) {
            throw KonsultasjonNotFoundForPatientException(patientId)
        }
    }
}
