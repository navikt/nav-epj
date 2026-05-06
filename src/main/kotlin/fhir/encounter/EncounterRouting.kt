package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.encounter.repository.StubEncounterRepository
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.respondFhir

fun Route.configureEncounterRouting() {
    val encounterService = EncounterService(StubEncounterRepository())
    get("/Encounter/{id}") {
        if (!call.isAuthenticated()) {
            call.respondRedirect("/login")
            return@get
        }
        val id =
            call.parameters["id"]
                ?: return@get call.respondText(
                    "Missing encounter id",
                    status = HttpStatusCode.BadRequest,
                )
        val encounter = encounterService.getEncounter(id)
        if (encounter != null) {
            call.respondFhir(encounter)
        } else {
            call.respondText("Encounter not found", status = HttpStatusCode.NotFound)
        }
    }

    get("/Encounter") {
        if (!call.isAuthenticated()) {
            call.respondRedirect("/login")
            return@get
        }
        val encounters = encounterService.getAllEncounters()
        val bundle =
            Bundle(
                type = Enumeration(value = Bundle.BundleType.Searchset),
                entry =
                    encounters.map { encounter ->
                        Bundle.Entry(
                            fullUrl = Uri(value = "Encounter/${encounter.id}"),
                            resource = encounter,
                        )
                    },
            )
        call.respondFhir(bundle)
    }

    post("/Encounter") {
        if (!call.isAuthenticated()) {
            call.respondRedirect("/login")
            return@post
        }
        val body = call.receiveText()
        val encounter = fhirJson.decodeFromString(body) as Encounter
        val created = encounterService.createEncounter(encounter)
        call.respondFhir(created)
    }
}
