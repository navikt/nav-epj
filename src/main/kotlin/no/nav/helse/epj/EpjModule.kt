package no.nav.helse.epj

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.helsepersonell.helsepersonellRoutes
import no.nav.helse.epj.konsultasjon.JournalnotatId
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.routes.diagnoseRoutes
import no.nav.helse.epj.konsultasjon.routes.journalnotatRoutes
import no.nav.helse.epj.konsultasjon.routes.konsultasjonRoutes
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.legekontor.LegekontorService
import no.nav.helse.epj.legekontor.legekontorRoutes
import no.nav.helse.epj.pasient.PasientId
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
      helsepersonellRoutes(helsepersonellService, legekontorService)
      konsultasjonRoutes(konsultasjonService, valkeyService)
      legekontorRoutes(legekontorService)
      diagnoseRoutes(konsultasjonService)
      journalnotatRoutes(konsultasjonService)
    }
  }
}

fun ApplicationCall.patientId(): PasientId = PasientId(uuidParameter("patientId"))

fun ApplicationCall.journalnotatId(): JournalnotatId =
  JournalnotatId(uuidParameter("journalnotatId"))

fun ApplicationCall.helsepersonellHpr(): HelsepersonellHpr =
  HelsepersonellHpr(stringParameter("hpr"))

fun ApplicationCall.legekontorId(): LegekontorId = LegekontorId(uuidParameter("legekontorId"))

fun ApplicationCall.konsultasjonId(): KonsultasjonId =
  KonsultasjonId(uuidParameter("konsultasjonId"))

private fun ApplicationCall.uuidParameter(name: String): Uuid {
  val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")

  return try {
    Uuid.parse(value)
  } catch (exception: IllegalArgumentException) {
    throw BadRequestException("Parameteren '$name' er ikke en gyldig UUID", exception)
  }
}

private fun ApplicationCall.stringParameter(name: String): String {
  val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")
  return value
}
