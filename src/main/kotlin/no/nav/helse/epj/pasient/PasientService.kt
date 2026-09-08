package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId

class PasientService(private val pasientRepository: PasientRepository) {
    suspend fun getPasienterByHpr(hpr: HelsepersonellHpr): List<Pasient> {
        return pasientRepository.listByHpr(hpr)
    }

    suspend fun getPasientById(id: PasientId): Pasient? {
        return pasientRepository.findById(id.value)
    }

    suspend fun getPasientByFnr(fnr: String): Pasient? {
        return pasientRepository.findByFnr(fnr)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createPasient(request: OpprettPasientRequest, hpr: String): Pasient {
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
