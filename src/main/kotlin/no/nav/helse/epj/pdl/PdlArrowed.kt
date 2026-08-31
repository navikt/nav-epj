package no.nav.helse.epj.pdl

import arrow.core.Either
import arrow.core.raise.either
import no.nav.tsm.pdl.PdlClient
import no.nav.tsm.pdl.Person

class PdlArrowed(private val pdlClient: PdlClient) {
  enum class PdlErrors {
    NotFound,
    UnknownError,
  }

  suspend fun getPerson(ident: String): Either<PdlErrors, Person> = either {
    try {
      pdlClient.getPerson(ident) ?: raise(PdlErrors.NotFound)
    } catch (_: PdlClient.UnknownError) {
      raise(PdlErrors.UnknownError)
    }
  }
}
