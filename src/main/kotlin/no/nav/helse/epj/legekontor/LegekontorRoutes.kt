package no.nav.helse.epj.legekontor

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.epj.legekontorId

fun Route.legekontorRoutes(legekontorService: LegekontorService) {
  route("api") {
    get("/legekontor/{legekontorId}") {
      val idParam = call.legekontorId()
      val legekontor = legekontorService.getLegekontor(idParam)
      call.respond(legekontor)
    }
  }
}
