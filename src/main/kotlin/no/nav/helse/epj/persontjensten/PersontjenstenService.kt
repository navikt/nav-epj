package no.nav.helse.epj.persontjensten

import no.nav.helse.epj.persontjensten.model.PersonName

class PersontjenstenService(val baseUrl: String) {

    suspend fun serachByFnr(fnr: String): PersonName? {

        return PersontjenstenHttpClient(
                baseUrl,
                dpopToken = "21312313", // TODO this is where i need my token
            )
            .getByNin(fnr)
    }
}
