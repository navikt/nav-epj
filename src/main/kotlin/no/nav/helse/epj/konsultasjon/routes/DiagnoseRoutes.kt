package no.nav.helse.epj.konsultasjon.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.konsultaasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.patientId

fun Route.diagnoseRoutes(konsultasjonService: KonsultasjonService) {

  val log = logger()

  route("/api") {
    route("/diagnoser") {
      get {
        val pasientId = call.parameters["patientId"]
        val konsultasjonId = call.parameters["konsultasjonId"]
        if (pasientId != null) {
          val id = call.patientId()
          val diagnoser = konsultasjonService.getDiagnoser(id)
          call.respond(diagnoser)
        }
        if (konsultasjonId != null) {
          val id = call.konsultaasjonId()
          val diagnoser = konsultasjonService.getDiagnoser(id)
          call.respond(diagnoser)
        }
        call.respond(HttpStatusCode.NotFound)
      }
    }
  }
}
