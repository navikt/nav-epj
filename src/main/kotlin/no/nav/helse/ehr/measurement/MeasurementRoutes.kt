package no.nav.helse.ehr.measurement

import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.ZoneOffset
import no.nav.helse.ehr.patientId
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.ehr.sessionorg.requireOrgNumber
import no.nav.helse.helseId.loggedInUser

fun Route.measurementRoutes(
    measurementService: MeasurementService,
    sessionOrgResolver: SessionOrgResolver,
) {
    route("/api/patient/{patientId}/measurements") {
        get {
            val orgNumber = sessionOrgResolver.requireOrgNumber(loggedInUser().hpr)
            call.respond(
                measurementService.getMeasurementsForPatient(call.patientId(), orgNumber).map {
                    it.toResponse()
                }
            )
        }
    }
}

private data class MeasurementResponse(
    val id: String,
    val patientId: String,
    val consultationId: String,
    val hpr: String?,
    val loincCode: String,
    val loincDisplay: String,
    val value: java.math.BigDecimal,
    val unitCode: String,
    val unitDisplay: String,
    val effectiveAt: String,
    val status: MeasurementStatus,
)

private fun Measurement.toResponse() =
    MeasurementResponse(
        id = id.value.toString(),
        patientId = patientId.value.toString(),
        consultationId = consultationId.value.toString(),
        hpr = hpr?.value,
        loincCode = loincCode,
        loincDisplay = loincDisplay,
        value = value,
        unitCode = unitCode,
        unitDisplay = unitDisplay,
        effectiveAt = effectiveAt.toInstant(ZoneOffset.UTC).toString(),
        status = status,
    )
