package no.nav.helse.epj.konsultasjon.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjonId
import no.nav.helse.epj.patientId

fun Route.diagnoseRoutes(konsultasjonService: KonsultasjonService) {

  route("/api") {
    route("/diagnoser") {
      get {
        val pasientId = call.parameters["patientId"]
        val konsultasjonId = call.parameters["konsultasjonId"]

        val diagnoser =
          when {
            pasientId != null -> konsultasjonService.getDiagnoser(call.patientId())
            konsultasjonId != null -> konsultasjonService.getDiagnoser(call.konsultasjonId())
            else -> emptyList()
          }
        call.respond(diagnoser)
      }
    }
  }
}
