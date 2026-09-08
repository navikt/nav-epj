package no.nav.helse.epj.persontjensten

import no.nav.helse.epj.persontjensten.model.PersonName

class PersontjenstenService(val baseUrl: String) {

    suspend fun serachByFnr(fnr: String): PersonName? {

        return PersontjenstenHttpClient(baseUrl).getByNin(fnr)
    }
}
