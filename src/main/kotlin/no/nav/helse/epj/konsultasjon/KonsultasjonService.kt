package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PasientId
import no.nav.tsm.diagnoser.Diagnose

class KonsultasjonService(private val konsultasjonRepository: KonsultasjonRepository) {
    val log = logger()

    suspend fun getKonsultasjoner(pasientId: PasientId, orgnummer: String?): List<Konsultasjon> {
        return konsultasjonRepository.listByPasientId(pasientId, orgnummer)
    }

    suspend fun getAktivKonsultasjon(pasientId: PasientId, orgnummer: String?): Konsultasjon? {
        return konsultasjonRepository.findActiveByPasientId(pasientId, orgnummer)
    }

    suspend fun getAktivKonsultasjon(
        pasientId: PasientId,
        hpr: HelsepersonellHpr,
        orgnummer: String?,
    ): Konsultasjon? {
        return konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, hpr, orgnummer)
    }

    suspend fun getKonsultasjon(konsultasjonId: KonsultasjonId, orgnummer: String?): Konsultasjon {
        return konsultasjonRepository.findByKonsultasjonId(konsultasjonId, orgnummer)
            ?: throw KonsultasjonNotFoundException(konsultasjonId)
    }

    suspend fun getDiagnoser(pasientId: PasientId, orgnummer: String?): List<Diagnose> {
        return konsultasjonRepository.listDiagnoser(pasientId, orgnummer)
    }

    suspend fun getDiagnoser(konsultasjonId: KonsultasjonId, orgnummer: String?): List<Diagnose> {
        return konsultasjonRepository.listDiagnoser(konsultasjonId, orgnummer)
    }

    suspend fun getJournalnotat(journalnotatId: JournalnotatId, orgnummer: String?): Journalnotat? {
        return konsultasjonRepository.findJournalnotat(journalnotatId, orgnummer)
    }

    suspend fun createJournalnotat(journalnotat: Journalnotat, orgnummer: String): Boolean {
        val insertedRows = konsultasjonRepository.insertJournalnotat(journalnotat, orgnummer)

        return insertedRows == 1
    }

    suspend fun getJournalnotater(
        pasientId: PasientId,
        konsultasjonId: KonsultasjonId?,
        orgnummer: String?,
    ): List<Journalnotat> {
        return konsultasjonRepository.listJournalnotat(pasientId, konsultasjonId, orgnummer)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun opprettJournalnotat(
        request: OpprettJournalnotatRequest,
        orgnummer: String,
    ): Journalnotat {
        val id = JournalnotatId(Uuid.generateV4())
        return konsultasjonRepository.opprettJournalnotat(id, request, orgnummer)
    }

    suspend fun createKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon): Konsultasjon {
        val createdId = konsultasjonRepository.insert(opprettKonsultasjon)
        val createdKonsultasjon =
            konsultasjonRepository.findByKonsultasjonId(createdId, opprettKonsultasjon.orgnummer)
                ?: throw IllegalStateException("Konsultasjon ble ikke opprettet")

        log.info(
            "Created konsultasjon id={} for pasientId={}",
            createdKonsultasjon.id,
            opprettKonsultasjon.pasientId,
        )
        return createdKonsultasjon
    }

    suspend fun getOrCreateKonsultasjon(
        pasientId: PasientId,
        hpr: HelsepersonellHpr,
        orgnummer: String,
    ): Konsultasjon {
        val aktivKonsultasjon = getAktivKonsultasjon(pasientId, hpr, orgnummer)
        if (aktivKonsultasjon != null) return aktivKonsultasjon
        val opprettKonsultasjon =
            OpprettKonsultasjon(
                pasientId = pasientId,
                hpr = listOf(hpr),
                startetTidspunkt = LocalDateTime.now(),
                status = KonsultasjonStatus.PAAGAAENDE,
                orgnummer = orgnummer,
            )
        return createKonsultasjon(opprettKonsultasjon)
    }

    suspend fun updateKonsultasjon(
        oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
        pasientId: PasientId,
        orgnummer: String?,
    ) {
        log.info("oppdater konsultasjon på pasientId: $pasientId")
        val updatedRows = konsultasjonRepository.update(oppdaterKonsultasjon, pasientId, orgnummer)
        if (updatedRows == 0) {
            throw KonsultasjonNotFoundForPatientException(pasientId)
        }
    }

    suspend fun cancelKonsultasjon(
        konsultasjonId: KonsultasjonId,
        pasientId: PasientId,
        orgnummer: String?,
    ) {
        log.info("avbryter konsultasjon {} for pasientId: {}", konsultasjonId, pasientId)
        val updatedRows = konsultasjonRepository.avbryt(konsultasjonId, pasientId, orgnummer)
        if (updatedRows == 0) {
            throw KonsultasjonNotFoundForPatientException(pasientId)
        }
    }
}
