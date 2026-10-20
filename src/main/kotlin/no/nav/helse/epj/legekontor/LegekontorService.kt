package no.nav.helse.epj.legekontor

import no.nav.helse.core.utils.LegekontorNotfoundException

class LegekontorService(val legekontorRepository: LegekontorRepository) {

    suspend fun getLegekontor(legekontorId: LegekontorId, orgnummer: String?): Legekontor {
        return legekontorRepository.findByLegekontorId(legekontorId.value)?.takeIf {
            orgnummer == null || it.orgnummer == orgnummer
        } ?: throw LegekontorNotfoundException()
    }
}
