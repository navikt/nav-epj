package no.nav.helse.epj

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import no.nav.helse.epj.clinic.ClinicId
import no.nav.helse.epj.clinic.ClinicService
import no.nav.helse.epj.clinic.clinicRoutes
import no.nav.helse.epj.consultation.ClinicalNoteId
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.consultation.ConsultationService
import no.nav.helse.epj.consultation.routes.clinicalNoteRoutes
import no.nav.helse.epj.consultation.routes.consultationRoutes
import no.nav.helse.epj.consultation.routes.diagnosisRoutes
import no.nav.helse.epj.measurement.MeasurementService
import no.nav.helse.epj.measurement.measurementRoutes
import no.nav.helse.epj.patient.ActivePatientService
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.patient.PatientService
import no.nav.helse.epj.patient.activePatientRoutes
import no.nav.helse.epj.patient.patientRoutes
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.epj.practitioner.PractitionerService
import no.nav.helse.epj.practitioner.practitionerRoutes
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.SessionOrgService
import no.nav.helse.epj.sessionorg.sessionOrgRoutes
import no.nav.helse.helseId.sessionRoutes
import no.nav.helse.smart.valkey.ValkeyService

fun Application.configureEpjModule() {
    val patientService: PatientService by dependencies
    val practitionerService: PractitionerService by dependencies
    val consultationService: ConsultationService by dependencies
    val clinicService: ClinicService by dependencies
    val measurementService: MeasurementService by dependencies
    val valkeyService: ValkeyService by dependencies
    val sessionOrgResolver: SessionOrgResolver by dependencies
    val sessionOrgService: SessionOrgService by dependencies
    val activePatientService =
        ActivePatientService(patientService, valkeyService, sessionOrgResolver)

    routing {
        authenticate("wonderwall-helseid") {
            singlePageApplication {
                useResources = true
                defaultPage = "index.html"
                filesPath = "static"
            }
            sessionRoutes()
            sessionOrgRoutes(sessionOrgResolver, sessionOrgService)
            patientRoutes(patientService, sessionOrgResolver)
            activePatientRoutes(activePatientService)
            practitionerRoutes(practitionerService)
            consultationRoutes(consultationService, activePatientService, sessionOrgResolver)
            clinicRoutes(clinicService, sessionOrgResolver)
            diagnosisRoutes(consultationService, sessionOrgResolver)
            clinicalNoteRoutes(consultationService, sessionOrgResolver)
            measurementRoutes(measurementService, sessionOrgResolver)
        }
    }
}

fun ApplicationCall.patientId(): PatientId = PatientId(uuidParameter("patientId"))

fun ApplicationCall.clinicalNoteId(): ClinicalNoteId =
    ClinicalNoteId(uuidParameter("clinicalNoteId"))

fun ApplicationCall.practitionerHpr(): PractitionerHpr = PractitionerHpr(stringParameter("hpr"))

fun ApplicationCall.clinicId(): ClinicId = ClinicId(uuidParameter("clinicId"))

fun ApplicationCall.consultationId(): ConsultationId =
    ConsultationId(uuidParameter("consultationId"))

private fun ApplicationCall.uuidParameter(name: String): Uuid {
    val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")

    return try {
        Uuid.parse(value)
    } catch (exception: IllegalArgumentException) {
        throw BadRequestException("Parameteren '$name' er ikke en gyldig UUID", exception)
    }
}

private fun ApplicationCall.stringParameter(name: String): String {
    val value = parameters[name] ?: throw BadRequestException("Mangler parameteren '$name'")
    return value
}
