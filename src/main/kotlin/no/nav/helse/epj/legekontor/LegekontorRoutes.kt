package no.nav.helse.epj.legekontor

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

fun Route.legekontorRoutes(legekontorService: LegekontorService) {
  route("api") {
    get("/legekontor/{legekontorId}") {
      val idParam =
        call.parameters["legekontorId"]
          ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
      val legekontorId = LegekontorId(Uuid.parse(idParam))
      val legekontor = legekontorService.getLegekontor(legekontorId)
      call.respond(legekontor)
    }
  }
}
