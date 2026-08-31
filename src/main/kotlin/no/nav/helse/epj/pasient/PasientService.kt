package no.nav.helse.epj.pasient

import arrow.core.getOrElse
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.core.utils.PasientNotFoundInPdlExeption
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.pdl.PdlArrowed

class PasientService(
  private val pasientRepository: PasientRepository,
  private val pdlClient: PdlArrowed,
) {
  private val logger = logger()

  suspend fun getPasienterByHpr(hpr: HelsepersonellHpr): List<Pasient> {
    return pasientRepository.listByHpr(hpr)
  }

  suspend fun getPasientById(id: PasientId): Pasient? {
    return pasientRepository.findById(id.value)
  }

  suspend fun createPasient(request: OpprettPasientRequest, hpr: String): Pasient {
    pdlClient.getPerson(request.fnr).getOrElse { throw PasientNotFoundInPdlExeption() }

    val newPasient =
      Pasient(
        id = PasientId(Uuid.generateV4()),
        legekontorId = LegekontorId(Legekontor.DEFAULT.id.value),
        hprNumbers = listOf(HelsepersonellHpr(hpr)),
        fornavn = request.fornavn,
        etternavn = request.etternavn,
        fnr = request.fnr,
      )
    pasientRepository.insert(newPasient)
    return pasientRepository.findByFnr(request.fnr) ?: throw PasientCreationException()
  }
}
