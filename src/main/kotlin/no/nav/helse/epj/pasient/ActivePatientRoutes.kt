package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.helse.helseId.loggedInUser
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService

data class ActivePatientRequest(val patientId: String)

data class ActivePatientResponse(val patientId: String, val expiresAt: Instant)

private fun ActivePatient.toResponse() = ActivePatientResponse(patientId, expiresAt)

fun Route.activePatientRoutes(
    activePatientService: ActivePatientService,
    valkeyService: ValkeyService,
) {
    route("/api/active-patient") {
        get {
            val active = valkeyService.getActivePatientWithExpiry(loggedInUser().hpr)
            if (active == null) call.respond(HttpStatusCode.NoContent)
            else call.respond(active.toResponse())
        }
        put {
            val request = call.receive<ActivePatientRequest>()
            val requested =
                Uuid.parseOrNull(request.patientId)
                    ?: return@put call.respond(HttpStatusCode.NotFound)
            val active =
                activePatientService.claimActivePatient(loggedInUser().hpr, requested)
                    ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(active.toResponse())
        }
    }
}
