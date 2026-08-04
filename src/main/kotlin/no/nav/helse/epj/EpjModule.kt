package no.nav.helse.epj

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.helsepersonell.helsepersonellRoutes
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.konsultasjonRoutes
import no.nav.helse.epj.legekontor.LegekontorService
import no.nav.helse.epj.legekontor.legekontorRoutes
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.epj.pasient.pasientRoutes
import no.nav.helse.smart.valkey.ValkeyService

fun Application.configureEpjModule() {
  val pasientService: PasientService by dependencies
  val helsepersonellService: HelsepersonellService by dependencies
  val konsultasjonService: KonsultasjonService by dependencies
  val legekontorService: LegekontorService by dependencies
  val valkeyService: ValkeyService by dependencies
  routing {
    authenticate("wonderwall-helseid") {
      singlePageApplication {
        useResources = true
        defaultPage = "index.html"
        filesPath = "static"
      }
      pasientRoutes(pasientService)
      helsepersonellRoutes(helsepersonellService)
      konsultasjonRoutes(konsultasjonService, valkeyService)
      legekontorRoutes(legekontorService)
    }
  }
}
