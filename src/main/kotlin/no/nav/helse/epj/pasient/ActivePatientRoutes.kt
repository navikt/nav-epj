package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.helseId.loggedInUser
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService

data class ActivePatientRequest(val patientId: String)

data class ActivePatientResponse(val patientId: String, val expiresAt: Instant)

private fun ActivePatient.toResponse() = ActivePatientResponse(patientId, expiresAt)

fun Route.activePatientRoutes(pasientService: PasientService, valkeyService: ValkeyService) {
    route("/api/active-patient") {
        get {
            val active = valkeyService.getActivePatientWithExpiry(loggedInUser().hpr)
            if (active == null) call.respond(HttpStatusCode.NoContent)
            else call.respond(active.toResponse())
        }
        put {
            val hpr = loggedInUser().hpr
            val requested =
                runCatching { Uuid.parse(call.receive<ActivePatientRequest>().patientId) }
                    .getOrNull() ?: return@put call.respond(HttpStatusCode.NotFound)
            val pasient =
                pasientService.getPasientById(PasientId(requested))
                    ?: return@put call.respond(HttpStatusCode.NotFound)
            if (HelsepersonellHpr(hpr) !in pasient.hprNumbers) {
                return@put call.respond(HttpStatusCode.NotFound)
            }
            valkeyService.setActivePatient(hpr, requested.toString())
            val active =
                valkeyService.getActivePatientWithExpiry(hpr)
                    ?: return@put call.respond(HttpStatusCode.InternalServerError)
            call.respond(active.toResponse())
        }
    }
}
