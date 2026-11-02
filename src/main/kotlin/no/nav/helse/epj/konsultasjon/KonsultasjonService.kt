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

    suspend fun getKonsultasjoner(pasientId: PasientId, orgNumber: String?): List<Konsultasjon> {
        return konsultasjonRepository.listByPasientId(pasientId, orgNumber)
    }

    suspend fun getAktivKonsultasjon(pasientId: PasientId, orgNumber: String?): Konsultasjon? {
        return konsultasjonRepository.findActiveByPasientId(pasientId, orgNumber)
    }

    suspend fun getAktivKonsultasjon(
        pasientId: PasientId,
        hpr: HelsepersonellHpr,
        orgNumber: String?,
    ): Konsultasjon? {
        return konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, hpr, orgNumber)
    }

    suspend fun getKonsultasjon(konsultasjonId: KonsultasjonId, orgNumber: String?): Konsultasjon {
        return konsultasjonRepository.findByKonsultasjonId(konsultasjonId, orgNumber)
            ?: throw KonsultasjonNotFoundException(konsultasjonId)
    }

    suspend fun getDiagnoser(pasientId: PasientId, orgNumber: String?): List<Diagnose> {
        return konsultasjonRepository.listDiagnoser(pasientId, orgNumber)
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
        pasientId: PasientId,
        konsultasjonId: KonsultasjonId?,
        orgNumber: String?,
    ): List<Journalnotat> {
        return konsultasjonRepository.listJournalnotat(pasientId, konsultasjonId, orgNumber)
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
            "Created konsultasjon id={} for pasientId={}",
            createdKonsultasjon.id,
            opprettKonsultasjon.pasientId,
        )
        return createdKonsultasjon
    }

    suspend fun getOrCreateKonsultasjon(
        pasientId: PasientId,
        hpr: HelsepersonellHpr,
        orgNumber: String,
    ): Konsultasjon {
        val aktivKonsultasjon = getAktivKonsultasjon(pasientId, hpr, orgNumber)
        if (aktivKonsultasjon != null) return aktivKonsultasjon
        val opprettKonsultasjon =
            OpprettKonsultasjon(
                pasientId = pasientId,
                hpr = listOf(hpr),
                startetTidspunkt = LocalDateTime.now(),
                status = KonsultasjonStatus.PAAGAAENDE,
                orgNumber = orgNumber,
            )
        return createKonsultasjon(opprettKonsultasjon)
    }

    suspend fun updateKonsultasjon(
        oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
        pasientId: PasientId,
        orgNumber: String?,
    ) {
        log.info("oppdater konsultasjon på pasientId: $pasientId")
        val updatedRows = konsultasjonRepository.update(oppdaterKonsultasjon, pasientId, orgNumber)
        if (updatedRows == 0) {
            throw KonsultasjonNotFoundForPatientException(pasientId)
        }
    }

    suspend fun cancelKonsultasjon(
        konsultasjonId: KonsultasjonId,
        pasientId: PasientId,
        orgNumber: String?,
    ) {
        log.info("avbryter konsultasjon {} for pasientId: {}", konsultasjonId, pasientId)
        val updatedRows = konsultasjonRepository.avbryt(konsultasjonId, pasientId, orgNumber)
        if (updatedRows == 0) {
            throw KonsultasjonNotFoundForPatientException(pasientId)
        }
    }
}
