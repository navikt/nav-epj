package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Uri
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.patient.repository.StubPatientRepository
import no.nav.helse.fhir.respondFhir

fun Route.configurePatientRouting() {
    val patientService = PatientService(StubPatientRepository())
    get("/Patient/{id}") {
        if (!call.isAuthenticated()) {
            call.respondRedirect("/login")
            return@get
        }

        val id =
            call.parameters["id"]
                ?: return@get call.respondText(
                    "Missing patient id",
                    status = HttpStatusCode.BadRequest,
                )

        val patient = patientService.getPatient(id)
        if (patient != null) {
            call.respondFhir(patient)
        } else {
            call.respondText("Patient not found", status = HttpStatusCode.NotFound)
        }
    }

    get("/Patient") {
        if (!call.isAuthenticated()) {
            call.respondRedirect("/login")
            return@get
        }

        val patients = patientService.getAllPatients()
        val bundle =
            Bundle(
                type = Enumeration(value = Bundle.BundleType.Searchset),
                entry =
                    patients.map { patient ->
                        Bundle.Entry(
                            fullUrl = Uri(value = "Patient/${patient.id}"),
                            resource = patient,
                        )
                    },
            )
        call.respondFhir(bundle)
    }

    post("/Patient") {
        if (!call.isAuthenticated()) {
            call.respondRedirect("/login")
            return@post
        }
        val body = call.receiveText()
        val patient = fhirJson.decodeFromString(body) as Patient
        val created = patientService.createPatient(patient)
        call.respondFhir(created)
    }
}
