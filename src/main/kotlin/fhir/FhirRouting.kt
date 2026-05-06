package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Resource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import no.nav.helse.auth.UserSession
import no.nav.helse.fhir.condition.configureConditionRouting
import no.nav.helse.fhir.documentreference.configureDocumentReferenceRouting
import no.nav.helse.fhir.encounter.configureEncounterRouting
import no.nav.helse.fhir.organization.configureOrganizationRouting
import no.nav.helse.fhir.patient.configurePatientRouting
import no.nav.helse.fhir.practitioner.configurePractitionerRouting

val fhirJson = FhirR4Json()
const val FHIR_CONTENT_TYPE = "application/fhir+json"

fun Application.configureFhirRouting() {
    routing {
        route("fhir") {
            configurePatientRouting()
            configureOrganizationRouting()
            configureEncounterRouting()
            configureConditionRouting()
            configurePractitionerRouting()
            configureDocumentReferenceRouting()
        }
    }
}

fun ApplicationCall.isAuthenticated(): Boolean {
    val session = sessions.get<UserSession>()
    return session?.accessToken != null
}

suspend inline fun <reified T : Resource> ApplicationCall.respondFhir(resource: T) {
    response.header(HttpHeaders.ContentType, FHIR_CONTENT_TYPE)
    respondText(fhirJson.encodeToString(resource), ContentType.Application.Json)
}
