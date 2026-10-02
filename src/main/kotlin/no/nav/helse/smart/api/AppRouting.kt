package no.nav.helse.smart.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.helseId.loggedInUser
import no.nav.helse.smart.security.LaunchMode
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.TokenEndpointAuthMethod

/** The registered client as the frontend may see it. Never carries secrets or key material. */
data class AppDto(
    val clientId: String,
    val navn: String,
    val beskrivelse: String,
    val ikon: String,
    val launchMode: LaunchMode,
    val launchUri: String?,
    val tokenEndpointAuthMethod: TokenEndpointAuthMethod,
    val jwksUri: String?,
    val redirectUris: List<String>,
    val scopes: List<String>,
)

fun SmartClient.toAppDto() =
    AppDto(
        clientId = clientId,
        navn = displayName,
        beskrivelse = beskrivelse,
        ikon = ikon,
        launchMode = launchMode,
        launchUri = launchUris.firstOrNull(),
        tokenEndpointAuthMethod = tokenEndpointAuthMethod,
        jwksUri = jwksUri,
        redirectUris = redirectUris,
        scopes = allowedScopes.map { it.toString() },
    )

data class LaunchRequest(val appId: String, val patientId: String)

data class LaunchResponse(val launchUrl: String)

data class LaunchError(val code: String, val message: String, val appId: String)

enum class LaunchErrorCode(val status: HttpStatusCode) {
    NO_ACTIVE_PATIENT(HttpStatusCode.Conflict),
    PATIENT_MISMATCH(HttpStatusCode.Conflict),
    NO_ACTIVE_ENCOUNTER(HttpStatusCode.Conflict),
    UNKNOWN_APP(HttpStatusCode.NotFound),
}

fun Route.appRoutes(
    clients: List<SmartClient>,
    fhirServerUrl: String,
    launchPreparer: LaunchPreparer,
) {
    route("/api") {
        get("/apps") { call.respond(clients.map { it.toAppDto() }) }

        post("/launch") {
            val request = call.receive<LaunchRequest>()
            val appId = request.appId

            suspend fun fail(code: LaunchErrorCode, message: String) =
                call.respond(code.status, LaunchError(code.name, message, appId))

            val client = clients.find { it.clientId == appId }
            val launchUri = client?.launchUris?.firstOrNull()
            if (client == null || launchUri == null) {
                return@post fail(LaunchErrorCode.UNKNOWN_APP, "The app is not registered")
            }

            when (val preparation = launchPreparer.prepare(loggedInUser().hpr, request.patientId)) {
                is LaunchPreparation.Ready ->
                    call.respond(
                        LaunchResponse(
                            buildLaunchUrl(launchUri, fhirServerUrl, preparation.launchId)
                        )
                    )
                LaunchPreparation.NoActivePatient ->
                    fail(
                        LaunchErrorCode.NO_ACTIVE_PATIENT,
                        "No active patient context for clinician",
                    )
                LaunchPreparation.PatientMismatch ->
                    fail(
                        LaunchErrorCode.PATIENT_MISMATCH,
                        "The active patient is not the requested patient",
                    )
                LaunchPreparation.UnknownPatient ->
                    fail(LaunchErrorCode.NO_ACTIVE_PATIENT, "Unknown patient")
                LaunchPreparation.NoActiveEncounter ->
                    fail(
                        LaunchErrorCode.NO_ACTIVE_ENCOUNTER,
                        "Found no active encounter for patient",
                    )
                LaunchPreparation.PatientWithoutId,
                LaunchPreparation.EncounterWithoutId ->
                    call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
