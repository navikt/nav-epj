package no.nav.helse.epj.konsultasjon.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.patientId

fun Route.diagnoseRoutes(konsultasjonService: KonsultasjonService) {

  val log = logger()

  route("/api") {
    route("/diagnoser/{patientId}") {
      get {
        val pasientId = call.patientId()
        val diagnoser = konsultasjonService.getDiagnoser(pasientId)
        call.respond(diagnoser)
      }
    }
  }
}
