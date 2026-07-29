package no.nav.helse.epj.konsultasjon

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PatientId

@OptIn(ExperimentalUuidApi::class)
class KonsultasjonService(private val konsultasjonRepository: KonsultasjonRepository) {
  val log = logger()

  suspend fun getKonsultasjoner(pasientId: PatientId): List<Konsultasjon> {
    return konsultasjonRepository.listByPasientId(pasientId)
  }

  suspend fun getAktivKonsultasjon(pasientId: PatientId): Konsultasjon? {
    return konsultasjonRepository.findActiveByPasientId(pasientId)
  }

  suspend fun getKonsultasjon(konsultasjonId: KonsultasjonId): Konsultasjon {
    return konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
      ?: throw KonsultasjonNotFoundException(konsultasjonId)
  }

  suspend fun createKonsultasjon(opprettKonsultasjon: OpprettKonsultasjon): Konsultasjon {
    val createdId = konsultasjonRepository.insert(opprettKonsultasjon)
    val createdKonsultasjon =
      konsultasjonRepository.findByKonsultasjonId(createdId)
        ?: throw IllegalStateException("Konsultasjon ble ikke opprettet")

    log.info(
      "Created konsultasjon id={} for pasientId={}",
      createdKonsultasjon.id,
      opprettKonsultasjon.pasientId,
    )
    return createdKonsultasjon
  }

  suspend fun getOrCreateKonsultasjon(pasientId: PatientId, hpr: HelsepersonellHpr): Konsultasjon {
    val aktivKonsultasjon = getAktivKonsultasjon(pasientId)
    if (aktivKonsultasjon != null) return aktivKonsultasjon
    val opprettKonsultasjon =
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf(hpr), // TODO: send inn liste med hpr i funksjonen - ikke kun en
        startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        status = KonsultasjonStatus.PÅGÅENDE,
      )
    return createKonsultasjon(opprettKonsultasjon)
  }

  suspend fun updateKonsultasjon(
    oppdaterKonsultasjon: OppdaterKonsultasjonRequest,
    pasientId: PatientId,
  ) {
    log.info("oppdater konsultasjon på pasientId: $pasientId")
    val updatedRows = konsultasjonRepository.update(oppdaterKonsultasjon, pasientId)
    if (updatedRows == 0) {
      throw KonsultasjonNotFoundForPatientException(oppdaterKonsultasjon.konsultasjonId, pasientId)
    }
  }
}
