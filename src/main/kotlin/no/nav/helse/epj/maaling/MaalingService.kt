package no.nav.helse.epj.maaling

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PasientId

class MaalingService(private val maalingRepository: MaalingRepository) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun opprettMaaling(request: OpprettMaalingRequest, orgNumber: String): Maaling {
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

        maalingRepository.insert(nyMaaling, orgNumber)
        return nyMaaling
    }

    suspend fun getMaaling(id: MaalingId, orgNumber: String?): Maaling? {
        return maalingRepository.findById(id, orgNumber)
    }

    suspend fun getMaalingerForPasient(pasientId: PasientId, orgNumber: String?): List<Maaling> {
        return maalingRepository.listByPasientId(pasientId, orgNumber)
    }

    suspend fun getMaalingerForKonsultasjon(
        konsultasjonId: KonsultasjonId,
        orgNumber: String?,
    ): List<Maaling> {
        return maalingRepository.listByKonsultasjonId(konsultasjonId, orgNumber)
    }
}
