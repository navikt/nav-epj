package no.nav.helse.epj.maaling

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PasientId

class MaalingService(private val maalingRepository: MaalingRepository) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun opprettMaaling(request: OpprettMaalingRequest): Maaling {
        val nyMaaling =
            Maaling(
                id = MaalingId(Uuid.generateV4()),
                pasientId = request.pasientId,
                konsultasjonId = request.konsultasjonId,
                hpr = request.hpr,
                loincKode = request.loincKode,
                loincVisningsnavn = request.loincVisningsnavn,
                verdi = request.verdi,
                enhetKode = request.enhetKode,
                enhetVisningsnavn = request.enhetVisningsnavn,
                effektivTidspunkt = request.effektivTidspunkt,
                status = request.status,
            )

        maalingRepository.insert(nyMaaling)
        return nyMaaling
    }

    suspend fun getMaaling(id: MaalingId): Maaling? {
        return maalingRepository.findById(id)
    }

    suspend fun getMaalingerForPasient(pasientId: PasientId): List<Maaling> {
        return maalingRepository.listByPasientId(pasientId)
    }

    suspend fun getMaalingerForKonsultasjon(konsultasjonId: KonsultasjonId): List<Maaling> {
        return maalingRepository.listByKonsultasjonId(konsultasjonId)
    }
}
