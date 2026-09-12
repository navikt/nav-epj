package no.nav.helse.epj.persontjensten

import no.nav.helse.core.Environment
import no.nav.helse.epj.persontjensten.model.PersonName

class PersontjenstenService(private val env: Environment) {

    suspend fun serachByFnr(fnr: String): PersonName? {

        return PersontjenstenHttpClient(
                baseUrl = env.persontjensten.baseUrl,
                dpopProf = "21312313",
                accessToken = "1321231",
            )
            .getByNin(fnr)
    }
}
