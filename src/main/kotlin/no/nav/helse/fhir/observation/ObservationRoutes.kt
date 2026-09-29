package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.encounterReferenceId
import no.nav.helse.fhir.observationId
import no.nav.helse.fhir.patientOrSubjectReferenceInputId
import no.nav.helse.fhir.security.requireFhirScope
import no.nav.helse.fhir.security.requirePatientMatch
import no.nav.helse.smart.security.Interaction

private const val LOINC_SYSTEM = "http://loinc.org"

fun Route.observationRoutes(
    observationService: ObservationService,
    fhirR4Json: FhirR4Json,
    fhirContentType: ContentType,
) {
    route("/fhir") {
        get("/Observation/{observation}") {
            val id = call.observationId()
            val principal = call.requireFhirScope("Observation", Interaction.READ)

            val observation =
                observationService.getObservationById(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        "No observation found for $id",
                    )
            principal.requirePatientMatch(
                "Observation",
                Interaction.READ,
                observation.subject?.reference?.value?.substringAfter("Patient/"),
            )

            call.respondText(fhirR4Json.encodeToString(observation), fhirContentType)
        }

        get("/Observation") {
            val patientId = call.patientOrSubjectReferenceInputId()
            val principal = call.requireFhirScope("Observation", Interaction.SEARCH)
            principal.requirePatientMatch(
                "Observation",
                Interaction.SEARCH,
                patientId.value.toString(),
            )

            val encounterId = call.parameters["encounter"]?.let { call.encounterReferenceId() }
            val code = call.parameters["code"]?.let(::parseObservationSearchCode)

            val bundle = observationService.searchObservations(patientId, encounterId, code)
            call.respondText(fhirR4Json.encodeToString(bundle), fhirContentType)
        }
    }
}

/**
 * Accepts either a bare LOINC code (`code=8310-5`) or a fully qualified `system|code` token
 * (`code=http://loinc.org|8310-5`). Any other system is rejected explicitly rather than silently
 * ignored, since LOINC is the only code system Maaling stores.
 */
private fun parseObservationSearchCode(raw: String): String {
    if (raw.isBlank()) {
        throw BadRequestException("Parameteren 'code' kan ikke være tom")
    }
    val separatorIndex = raw.indexOf('|')
    if (separatorIndex < 0) return raw

    val system = raw.substring(0, separatorIndex)
    val code = raw.substring(separatorIndex + 1)
    if (system != LOINC_SYSTEM || code.isEmpty()) {
        throw BadRequestException(
            "Parameteren 'code' må enten være en bar LOINC-kode eller '$LOINC_SYSTEM|<kode>'"
        )
    }
    return code
}
