package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId

@OptIn(ExperimentalUuidApi::class)
class PasientService(private val pasientRepository: PasientRepository) {
  private val logger = logger()

  suspend fun getPasienterByHpr(hpr: HelsepersonellHpr): List<Pasient> {
    return pasientRepository.findbyHpr(hpr.value)
  }

  suspend fun getPasientById(id: PatientId): Pasient? {
    return pasientRepository.findById(id.value)
  }

  suspend fun createPasient(request: OpprettPasientRequest, hpr: String): Pasient {
    val newPasient =
      Pasient(
        id = PatientId(Uuid.generateV4()),
        legekontorId = LegekontorId(Legekontor.DEFAULT.id.value),
        hpr = HelsepersonellHpr(hpr),
        navn = request.navn,
        fnr = request.fnr,
      )
    val insertPasient = pasientRepository.insert(newPasient)
    logger.info("inserted count: ${insertPasient.insertedCount}")
    return pasientRepository.findByFnr(request.fnr) ?: throw PasientCreationException()
  }
}
