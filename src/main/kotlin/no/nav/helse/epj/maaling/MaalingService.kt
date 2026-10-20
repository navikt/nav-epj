package no.nav.helse.epj.maaling

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PasientId

class MaalingService(private val maalingRepository: MaalingRepository) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun opprettMaaling(request: OpprettMaalingRequest, orgnummer: String): Maaling {
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

        maalingRepository.insert(nyMaaling, orgnummer)
        return nyMaaling
    }

    suspend fun getMaaling(id: MaalingId, orgnummer: String?): Maaling? {
        return maalingRepository.findById(id, orgnummer)
    }

    suspend fun getMaalingerForPasient(pasientId: PasientId, orgnummer: String?): List<Maaling> {
        return maalingRepository.listByPasientId(pasientId, orgnummer)
    }

    suspend fun getMaalingerForKonsultasjon(
        konsultasjonId: KonsultasjonId,
        orgnummer: String?,
    ): List<Maaling> {
        return maalingRepository.listByKonsultasjonId(konsultasjonId, orgnummer)
    }
}
