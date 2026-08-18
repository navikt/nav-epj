package no.nav.helse.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import no.nav.helse.core.utils.AktivKonsultasjonNotFoundException
import no.nav.helse.core.utils.HelsepersonellForPatientNotFoundException
import no.nav.helse.core.utils.HelsepersonellNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.LegekontorNotfoundException
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.core.utils.logger

fun Application.configureStatusPages() {
  val log = logger()
  install(StatusPages) {
    exception<KonsultasjonNotFoundException> { call, cause ->
      call.respondText(
        text = "Konsultasjon not found: ${cause.message}",
        status = HttpStatusCode.NotFound,
      )
    }
    exception<KonsultasjonNotFoundForPatientException> { call, cause ->
      call.respondText(
        text = "Konsultasjon not found for patient: ${cause.message}",
        status = HttpStatusCode.NotFound,
      )
    }
    exception<AktivKonsultasjonNotFoundException> { call, cause ->
      call.respondText(
        text = "Aktiv konsultasjon not found: ${cause.message}",
        status = HttpStatusCode.NotFound,
      )
    }
    exception<HelsepersonellNotFoundException> { call, cause ->
      call.respondText(
        text = "Helsepersonell not found: ${cause.message}",
        status = HttpStatusCode.NotFound,
      )
    }
    exception<LegekontorNotfoundException> { call, cause ->
      call.respondText(
        text = "Legekontor not found: ${cause.message}",
        status = HttpStatusCode.NotFound,
      )
    }
    exception<UgyldigDiagnoseException> { call, cause ->
      call.respondText(
        text = "Ugyldig diagnose: ${cause.message}",
        status = HttpStatusCode.BadRequest,
      )
    }
    exception<BadRequestException> { call, cause ->
      call.respondText(
        text = cause.message ?: "Ugyldig forespørsel",
        status = HttpStatusCode.BadRequest,
      )
    }
    exception<PasientCreationException> { call, cause ->
      log.error("Pasient ble ikke opprettet", cause)
      call.respondText(
        text = "En uventet feil oppstod ved opprettelse av pasient",
        status = HttpStatusCode.InternalServerError,
      )
    }
    exception<HelsepersonellForPatientNotFoundException> { call, cause ->
      log.error("Uventet feil i helsepersonell-API", cause)
      call.respondText(
        text = "En uventet feil oppstod",
        status = HttpStatusCode.InternalServerError,
      )
    }
    exception<Throwable> { call, cause ->
      log.error("Uventet feil", cause)
      call.respondText(
        text = "En uventet feil oppstod",
        status = HttpStatusCode.InternalServerError,
      )
    }
  }
}
