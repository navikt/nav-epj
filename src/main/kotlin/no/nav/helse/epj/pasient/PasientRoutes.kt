package no.nav.helse.epj.pasient

import io.ktor.http.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.core.utils.securelog
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.patientId
import no.nav.helse.epj.persontjensten.PersontjenstenService
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.loggedInUser
import tools.jackson.module.kotlin.jacksonMapperBuilder

private val securelog = securelog()

fun Route.pasientRoutes(
    pasientService: PasientService,
    persontjenstenService: PersontjenstenService,
) {

    route("/api") {
        route("/patient") {
            get {
                val principal = loggedInUser()
                val hpr = HelsepersonellHpr(principal.hpr)
                val pasient = pasientService.getPasienterByHpr(hpr)
                call.respond(pasient)
            }
            post {
                val principal = loggedInUser()
                val request = call.receive<OpprettPasientRequest>()
                val pasient = pasientService.createPasient(request, principal.hpr)
                call.respond(HttpStatusCode.Created, pasient)
            }
            get("/{patientId}") {
                val id = call.patientId()
                val pasient =
                    pasientService.getPasientById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Pasient not found")
                call.respond(pasient)
            }
            post("/serach/{pasientFnr}") {
                val pasientFnr = call.receiveText()
                val pasientInDb = pasientService.getPasientByFnr(pasientFnr)
                if (pasientInDb != null) {
                    return@post call.respond(pasientFnr)
                }

                securelog.info(
                    "logger tokens: ${
            jacksonMapperBuilder().build()
              .writeValueAsString(call.principal<HelseIdPrincipal>()?.debug)
          }"
                )

                val personFraPersontjensten = persontjenstenService.serachByFnr(pasientFnr)

                if (personFraPersontjensten != null) {
                    val opprettPasientRequest =
                        OpprettPasientRequest(
                            fornavn = personFraPersontjensten.givenName!!,
                            etternavn = personFraPersontjensten.familyName!!,
                            fnr = pasientFnr,
                        )

                    val principal = loggedInUser()
                    pasientService.createPasient(opprettPasientRequest, principal.hpr)
                }
            }
        }
    }
}
