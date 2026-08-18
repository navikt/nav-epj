package no.nav.helse.epj.konsultasjon.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.journalnotatId
import no.nav.helse.epj.konsultasjon.Journalnotat
import no.nav.helse.epj.konsultasjon.KonsultasjonService

fun Route.journalnotatRoutes(konsultasjonService: KonsultasjonService) {

  val log = logger()

  route("/api") {
    route("/journalnotat") {
      get("/{journalnotatId}") {
        val id = call.journalnotatId()
        val journalnotat =
          konsultasjonService.getJournalnotat(id) ?: call.respond(HttpStatusCode.NotFound)
        log.info("journalnotat: $journalnotat")
        call.respond(journalnotat)
      }
      post {
        val request = call.receive<Journalnotat>()
        if (konsultasjonService.createJournalnotat(request)) {
          call.respond(HttpStatusCode.Created)
        } else {
          call.respond(HttpStatusCode.InternalServerError, "Kunne ikke opprette journalnotat")
        }
      }
    }
  }
}
