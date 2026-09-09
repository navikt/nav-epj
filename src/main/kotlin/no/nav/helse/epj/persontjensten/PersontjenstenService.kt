package no.nav.helse.epj.persontjensten

import no.nav.helse.core.Environment
import no.nav.helse.epj.persontjensten.model.PersonName

class PersontjenstenService(private val env: Environment) {

    suspend fun serachByFnr(fnr: String): PersonName? {

        return PersontjenstenHttpClient(
                env.persontjensten.baseUrl,
                dpopToken = "21312313", // TODO this is where i need my token
            )
            .getByNin(fnr)
    }
}
