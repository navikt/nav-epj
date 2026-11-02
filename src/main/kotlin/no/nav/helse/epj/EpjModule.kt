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
import no.nav.helse.epj.konsultasjon.JournalnotatId
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.routes.diagnoseRoutes
import no.nav.helse.epj.konsultasjon.routes.journalnotatRoutes
import no.nav.helse.epj.konsultasjon.routes.konsultasjonRoutes
import no.nav.helse.epj.maaling.MaalingService
import no.nav.helse.epj.maaling.maalingRoutes
import no.nav.helse.epj.pasient.ActivePatientService
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.epj.pasient.activePatientRoutes
import no.nav.helse.epj.pasient.pasientRoutes
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.epj.practitioner.PractitionerService
import no.nav.helse.epj.practitioner.practitionerRoutes
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.epj.sessionorg.SessionOrgService
import no.nav.helse.epj.sessionorg.sessionOrgRoutes
import no.nav.helse.helseId.sessionRoutes
import no.nav.helse.smart.valkey.ValkeyService

fun Application.configureEpjModule() {
    val pasientService: PasientService by dependencies
    val practitionerService: PractitionerService by dependencies
    val konsultasjonService: KonsultasjonService by dependencies
    val clinicService: ClinicService by dependencies
    val maalingService: MaalingService by dependencies
    val valkeyService: ValkeyService by dependencies
    val sessionOrgResolver: SessionOrgResolver by dependencies
    val sessionOrgService: SessionOrgService by dependencies
    val activePatientService =
        ActivePatientService(pasientService, valkeyService, sessionOrgResolver)

    routing {
        authenticate("wonderwall-helseid") {
            singlePageApplication {
                useResources = true
                defaultPage = "index.html"
                filesPath = "static"
            }
            sessionRoutes()
            sessionOrgRoutes(sessionOrgResolver, sessionOrgService)
            pasientRoutes(pasientService, sessionOrgResolver)
            activePatientRoutes(activePatientService)
            practitionerRoutes(practitionerService)
            konsultasjonRoutes(konsultasjonService, activePatientService, sessionOrgResolver)
            clinicRoutes(clinicService, sessionOrgResolver)
            diagnoseRoutes(konsultasjonService, sessionOrgResolver)
            journalnotatRoutes(konsultasjonService, sessionOrgResolver)
            maalingRoutes(maalingService, sessionOrgResolver)
        }
    }
}

fun ApplicationCall.patientId(): PasientId = PasientId(uuidParameter("patientId"))

fun ApplicationCall.journalnotatId(): JournalnotatId =
    JournalnotatId(uuidParameter("journalnotatId"))

fun ApplicationCall.practitionerHpr(): PractitionerHpr = PractitionerHpr(stringParameter("hpr"))

fun ApplicationCall.clinicId(): ClinicId = ClinicId(uuidParameter("clinicId"))

fun ApplicationCall.konsultasjonId(): KonsultasjonId =
    KonsultasjonId(uuidParameter("konsultasjonId"))

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
