package no.nav.helse.smart.api

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import java.util.UUID
import kotlin.uuid.Uuid
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.smart.valkey.LaunchContext
import no.nav.helse.smart.valkey.ValkeyService

sealed interface LaunchPreparation {
    data class Ready(val launchId: String) : LaunchPreparation

    data object NoActivePatient : LaunchPreparation

    data object UnknownPatient : LaunchPreparation

    data object NoActiveEncounter : LaunchPreparation

    data object PatientWithoutId : LaunchPreparation

    data object EncounterWithoutId : LaunchPreparation
}

fun buildLaunchUrl(launchUri: String, iss: String, launchId: String): String =
    URLBuilder(launchUri)
        .apply {
            appendPathSegments("")
            parameters.append("iss", iss)
            parameters.append("launch", launchId)
        }
        .buildString()

/**
 * The checks and side effect shared by `GET /fhir/launch` and `POST /api/launch`: the clinician
 * needs an active patient with an ongoing encounter, and a successful check stores a new single-use
 * launch context.
 */
class LaunchPreparer(
    private val valkeyService: ValkeyService,
    private val patientService: PatientService,
    private val encounterService: EncounterService,
) {
    suspend fun prepare(hpr: String): LaunchPreparation {
        val activePatientId =
            valkeyService.getActivePatient(hpr) ?: return LaunchPreparation.NoActivePatient
        return prepareFor(PatientInputId(Uuid.parse(activePatientId)), hpr)
    }

    private suspend fun prepareFor(patientInputId: PatientInputId, hpr: String): LaunchPreparation {
        val patient =
            patientService.getPatient(patientInputId) ?: return LaunchPreparation.UnknownPatient
        val encounter =
            encounterService.getActiveEncounterByPatient(patientInputId)
                ?: return LaunchPreparation.NoActiveEncounter
        val patientId = patient.id
        val encounterId = encounter.id

        return when {
            patientId == null -> LaunchPreparation.PatientWithoutId
            encounterId == null -> LaunchPreparation.EncounterWithoutId
            else -> LaunchPreparation.Ready(saveLaunchContext(patientId, encounterId, hpr))
        }
    }

    private suspend fun saveLaunchContext(
        patientId: String,
        encounterId: String,
        hpr: String,
    ): String {
        val launchId = UUID.randomUUID().toString()
        valkeyService.saveLaunchContext(launchId, LaunchContext(patientId, encounterId, hpr))
        return launchId
    }
}
