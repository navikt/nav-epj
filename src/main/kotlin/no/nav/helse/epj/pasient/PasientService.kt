package no.nav.helse.epj.pasient

import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId

class PasientService(private val pasientRepository: PasientRepository) {
  private val logger = logger()

  suspend fun getPasienterByHpr(hpr: HelsepersonellHpr): List<Pasient> {
    return pasientRepository.listByHpr(hpr)
  }

  suspend fun getPasientById(id: PatientId): Pasient? {
    return pasientRepository.findById(id.value)
  }

  suspend fun createPasient(request: OpprettPasientRequest, hpr: String): Pasient {
    val newPasient =
      Pasient(
        id = PatientId(Uuid.generateV4()),
        legekontorId = LegekontorId(Legekontor.DEFAULT.id.value),
        hprNumbers = listOf(HelsepersonellHpr(hpr)),
        navn = request.navn,
        fnr = request.fnr,
      )
    pasientRepository.insert(newPasient)
    return pasientRepository.findByFnr(request.fnr) ?: throw PasientCreationException()
  }
}
