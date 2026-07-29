package no.nav.helse.epj.legekontor

import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.legekontorRoutes(legekontorService: LegekontorService) {
  route("api") {
    route("/legekontor") {
      get {
        val legekontor = legekontorService.getLegekontor()
        call.respond(legekontor)
      }
    }
  }
}
